package com.permieware.osmapdigger.desktop.map

import ch.poole.geo.pmtiles.Reader
import com.permieware.osmapdigger.desktop.diagnostics.DesktopDiagnostics
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.map.MapOverlayGeoJson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Loopback-only asset and vector-tile server used by the Intel macOS JCEF renderer.
 *
 * The server does not expose a network service beyond the local machine. PMTiles remains the
 * authoritative packaged map artifact; this adapter only translates local z/x/y requests into
 * random-access PMTiles reads for MapLibre GL JS.
 */
internal class LocalWebMapServer(
    private val datasetInfo: DatasetInfo,
    styleJson: String,
    pmtilesPath: Path,
    private val onSettlementActivated: (String) -> Unit = {},
    private val onMapLocationActivated: (GeoPoint) -> Unit = {},
) : Closeable {
    private val reader = Reader(pmtilesPath.toFile())
    private val stateJson = AtomicReference(emptyStateJson())
    private val closed = AtomicBoolean(false)
    private val requestCount = AtomicLong(0)
    private val tileRequestCount = AtomicLong(0)
    private val notFoundCount = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    private val clientCancelledCount = AtomicLong(0)
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "osmapdigger-web-map").apply { isDaemon = true }
        }
    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0).apply {
            executor = this@LocalWebMapServer.executor
        }
    private val baseUrl: String
    private val rewrittenStyle: String

    val indexUrl: String
        get() = "$baseUrl/index.html"

    init {
        require(reader.tileType.toInt() == TILE_TYPE_MVT) {
            "Intel macOS web map currently requires MVT PMTiles; tile type=${reader.tileType.toInt()}"
        }
        require(reader.tileCompression.toInt() in setOf(COMPRESSION_NONE, COMPRESSION_GZIP)) {
            "Unsupported PMTiles tile compression: ${reader.tileCompression.toInt()}"
        }

        baseUrl = "http://127.0.0.1:${server.address.port}"
        rewrittenStyle = rewriteStyle(styleJson)
        server.createContext("/", ::handle)
        server.start()
        DesktopDiagnostics.info(
            "map.server",
            "started dataset=${datasetInfo.id} address=$baseUrl pmtiles=${pmtilesPath.toAbsolutePath()} sizeBytes=${Files.size(pmtilesPath)} minZoom=${reader.minZoom.toInt() and 0xff} maxZoom=${reader.maxZoom.toInt() and 0xff} compression=${reader.tileCompression.toInt()}",
        )
    }

    fun updateState(
        results: List<Settlement>,
        selected: Settlement?,
        mapLocationPickingEnabled: Boolean = false,
    ): String {
        val next =
            buildJsonObject {
                put("results", Json.parseToJsonElement(MapOverlayGeoJson.build(results)))
                put("selected", Json.parseToJsonElement(MapOverlayGeoJson.build(listOfNotNull(selected))))
                put("mapLocationPickingEnabled", mapLocationPickingEnabled)
            }.toString()
        stateJson.set(next)
        return next
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        server.stop(0)
        executor.shutdownNow()
        runCatching { reader.close() }
            .onFailure { failure -> DesktopDiagnostics.warn("map.server", "PMTiles reader close failed", failure) }
        DesktopDiagnostics.info(
            "map.server",
            "stopped dataset=${datasetInfo.id} requests=${requestCount.get()} tiles=${tileRequestCount.get()} notFound=${notFoundCount.get()} cancelled=${clientCancelledCount.get()} errors=${errorCount.get()}",
        )
    }

    private fun handle(exchange: HttpExchange) {
        requestCount.incrementAndGet()
        try {
            val path = exchange.requestURI.path
            if (path == SETTLEMENT_INTERACTION_PATH) {
                handleSettlementInteraction(exchange)
                return
            }
            if (path == MAP_LOCATION_INTERACTION_PATH) {
                handleMapLocationInteraction(exchange)
                return
            }
            if (exchange.requestMethod != "GET") {
                send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".toByteArray())
                return
            }

            when (path) {
                "/", "/index.html" -> sendText(exchange, "text/html; charset=utf-8", indexHtml())
                "/style.json" -> sendText(exchange, "application/json; charset=utf-8", rewrittenStyle)
                "/state.json" -> {
                    exchange.responseHeaders.set("Cache-Control", "no-store")
                    sendText(exchange, "application/json; charset=utf-8", stateJson.get())
                }
                "/maplibre-gl.js" -> sendClasspathAsset(
                    exchange,
                    MAPLIBRE_JS_RESOURCE,
                    "text/javascript; charset=utf-8",
                )
                "/maplibre-gl.css" -> sendClasspathAsset(
                    exchange,
                    MAPLIBRE_CSS_RESOURCE,
                    "text/css; charset=utf-8",
                )
                else -> {
                    val match = TILE_PATH.matchEntire(path)
                    if (match == null) {
                        send(exchange, 404, "text/plain; charset=utf-8", "Not found".toByteArray())
                    } else {
                        tileRequestCount.incrementAndGet()
                        sendTile(
                            exchange,
                            zoom = match.groupValues[1].toInt(),
                            x = match.groupValues[2].toInt(),
                            y = match.groupValues[3].toInt(),
                        )
                    }
                }
            }
        } catch (cancelled: ClientDisconnectedException) {
            clientCancelledCount.incrementAndGet()
        } catch (failure: Throwable) {
            DesktopDiagnostics.error(
                "map.server",
                "request failed method=${exchange.requestMethod} path=${exchange.requestURI.path}",
                failure,
            )
            runCatching {
                send(
                    exchange,
                    500,
                    "text/plain; charset=utf-8",
                    (failure.message ?: failure.toString()).toByteArray(StandardCharsets.UTF_8),
                )
            }
        } finally {
            exchange.close()
        }
    }

    private fun handleSettlementInteraction(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".toByteArray())
            return
        }

        val body = exchange.requestBody.readNBytes(MAX_INTERACTION_BODY_BYTES + 1)
        if (body.size > MAX_INTERACTION_BODY_BYTES) {
            send(exchange, 413, "text/plain; charset=utf-8", "Interaction payload too large".toByteArray())
            return
        }
        val settlementId = decodeSettlementInteraction(body.toString(StandardCharsets.UTF_8))
        if (settlementId == null) {
            send(exchange, 400, "text/plain; charset=utf-8", "Invalid settlement interaction".toByteArray())
            return
        }

        DesktopDiagnostics.info("map.interaction", "settlement activated id=$settlementId")
        onSettlementActivated(settlementId)
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(204, -1)
    }

    private fun handleMapLocationInteraction(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".toByteArray())
            return
        }

        val body = exchange.requestBody.readNBytes(MAX_INTERACTION_BODY_BYTES + 1)
        if (body.size > MAX_INTERACTION_BODY_BYTES) {
            send(exchange, 413, "text/plain; charset=utf-8", "Interaction payload too large".toByteArray())
            return
        }
        val location = decodeMapLocationInteraction(body.toString(StandardCharsets.UTF_8))
        if (location == null) {
            send(exchange, 400, "text/plain; charset=utf-8", "Invalid map location interaction".toByteArray())
            return
        }

        DesktopDiagnostics.info(
            "map.interaction",
            "map location activated lat=${location.latitude} lon=${location.longitude}",
        )
        onMapLocationActivated(location)
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(204, -1)
    }

    private fun sendTile(
        exchange: HttpExchange,
        zoom: Int,
        x: Int,
        y: Int,
    ) {
        val tile = reader.getTile(zoom, x, y)
        if (tile == null) {
            send(exchange, 404, "text/plain; charset=utf-8", ByteArray(0))
            return
        }

        exchange.responseHeaders.set("Cache-Control", "public, max-age=3600")
        if (reader.tileCompression.toInt() == COMPRESSION_GZIP) {
            exchange.responseHeaders.set("Content-Encoding", "gzip")
        }
        send(exchange, 200, "application/vnd.mapbox-vector-tile", tile)
    }

    private fun sendClasspathAsset(
        exchange: HttpExchange,
        resource: String,
        contentType: String,
    ) {
        val bytes =
            javaClass.classLoader.getResourceAsStream(resource)?.use { it.readBytes() }
                ?: error("Required WebJar asset is missing: $resource")
        exchange.responseHeaders.set("Cache-Control", "public, max-age=86400")
        send(exchange, 200, contentType, bytes)
    }

    private fun sendText(
        exchange: HttpExchange,
        contentType: String,
        text: String,
    ) {
        send(exchange, 200, contentType, text.toByteArray(StandardCharsets.UTF_8))
    }

    private fun send(
        exchange: HttpExchange,
        status: Int,
        contentType: String,
        bytes: ByteArray,
    ) {
        if (status == 404) notFoundCount.incrementAndGet()
        if (status >= 500) errorCount.incrementAndGet()
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.responseHeaders.set("Access-Control-Allow-Origin", baseUrl)
        try {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            if (bytes.isNotEmpty()) {
                exchange.responseBody.write(bytes)
            }
        } catch (failure: IOException) {
            if (isExpectedClientDisconnect(failure)) {
                throw ClientDisconnectedException(failure)
            }
            throw failure
        }
    }

    private fun rewriteStyle(styleJson: String): String {
        val root = Json.parseToJsonElement(styleJson).jsonObject
        val sources = root["sources"]?.jsonObject ?: error("Map style has no sources")
        var rewrittenSourceCount = 0

        val rewrittenSources =
            JsonObject(
                sources.mapValues { (_, element) ->
                    val source = element.jsonObject
                    val isPmtilesVectorSource =
                        source["type"]?.jsonPrimitive?.content == "vector" &&
                            source["url"]?.jsonPrimitive?.content?.startsWith("pmtiles://") == true
                    if (!isPmtilesVectorSource) {
                        source
                    } else {
                        rewrittenSourceCount += 1
                        buildJsonObject {
                            source.forEach { (key, value) ->
                                if (key != "url") put(key, value)
                            }
                            put("tiles", JsonArray(listOf(JsonPrimitive("$baseUrl/tiles/{z}/{x}/{y}.pbf"))))
                            put("minzoom", reader.minZoom.toInt() and 0xff)
                            put("maxzoom", reader.maxZoom.toInt() and 0xff)
                            if (source["attribution"] == null) {
                                put("attribution", "© OpenStreetMap contributors")
                            }
                        }
                    }
                },
            )

        require(rewrittenSourceCount > 0) { "Map style has no PMTiles vector source" }
        return buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "sources") put(key, value)
            }
            put("sources", rewrittenSources)
        }.toString()
    }

    private fun indexHtml(): String =
        """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <link rel="stylesheet" href="/maplibre-gl.css">
          <style>
            html, body, #map { width: 100%; height: 100%; margin: 0; overflow: hidden; }
            body { background: #f4f1e8; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script src="/maplibre-gl.js"></script>
          <script>
            const emptyCollection = { type: 'FeatureCollection', features: [] };
            const map = new maplibregl.Map({
              container: 'map',
              style: '/style.json',
              center: [${datasetInfo.center.longitude}, ${datasetInfo.center.latitude}],
              zoom: ${datasetInfo.initialZoom},
              attributionControl: true
            });

            map.addControl(new maplibregl.ScaleControl({ maxWidth: 110, unit: 'metric' }), 'bottom-right');

            function ensureOverlay(id, data, paint) {
              if (!map.getSource(id)) {
                map.addSource(id, { type: 'geojson', data: data || emptyCollection });
                map.addLayer({ id: id, type: 'circle', source: id, paint: paint });
              }
            }

            function ensureSettlementLabelLayer(id, sourceId, selected) {
              if (map.getLayer(id)) return;
              map.addLayer({
                id: id,
                type: 'symbol',
                source: sourceId,
                minzoom: selected ? 0 : 8,
                layout: {
                  'text-field': ['get', 'name'],
                  'text-font': ['Arial'],
                  'text-size': selected ? 13 : 12,
                  'text-anchor': 'bottom',
                  'text-offset': [0, -0.75],
                  'text-allow-overlap': selected,
                  'text-ignore-placement': selected
                },
                paint: {
                  'text-color': selected ? '#D84315' : '#263238',
                  'text-halo-color': '#ffffff',
                  'text-halo-width': selected ? 2 : 1.5
                }
              });
            }

            let mapLocationPickingEnabled = false;

            function applyState(state, focusSelected) {
              mapLocationPickingEnabled = !!state.mapLocationPickingEnabled;
              if (!map.loaded()) return;
              const results = state.results || emptyCollection;
              const selected = state.selected || emptyCollection;
              ensureOverlay('osmapdigger-results', results, {
                'circle-color': '#2E7D32',
                'circle-radius': 5,
                'circle-stroke-color': '#ffffff',
                'circle-stroke-width': 1
              });
              ensureOverlay('osmapdigger-selected', selected, {
                'circle-color': '#D84315',
                'circle-radius': 8,
                'circle-stroke-color': '#ffffff',
                'circle-stroke-width': 2
              });
              ensureSettlementLabelLayer('osmapdigger-result-labels', 'osmapdigger-results', false);
              ensureSettlementLabelLayer('osmapdigger-selected-label', 'osmapdigger-selected', true);
              map.getSource('osmapdigger-results').setData(results);
              map.getSource('osmapdigger-selected').setData(selected);

              if (focusSelected && selected.features && selected.features.length > 0) {
                const coordinates = selected.features[0].geometry.coordinates;
                map.easeTo({ center: coordinates, zoom: Math.max(map.getZoom(), 11) });
              }
            }

            async function notifySettlementActivated(settlementId) {
              try {
                await fetch('${SETTLEMENT_INTERACTION_PATH}', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ id: settlementId })
                });
              } catch (error) {
                console.error('Could not report OsmapDigger map interaction', error);
              }
            }

            async function notifyMapLocationActivated(lngLat) {
              try {
                await fetch('${MAP_LOCATION_INTERACTION_PATH}', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ latitude: lngLat.lat, longitude: lngLat.lng })
                });
              } catch (error) {
                console.error('Could not report OsmapDigger map location interaction', error);
              }
            }

            window.osmapdiggerApplyState = applyState;
            map.on('click', (event) => {
              if (mapLocationPickingEnabled) {
                notifyMapLocationActivated(event.lngLat);
                return;
              }
              const layers = ['osmapdigger-selected', 'osmapdigger-results'].filter(id => map.getLayer(id));
              if (layers.length === 0) return;
              const hitRadius = ${SETTLEMENT_INTERACTION_HIT_RADIUS_PX};
              const hitBox = [
                [event.point.x - hitRadius, event.point.y - hitRadius],
                [event.point.x + hitRadius, event.point.y + hitRadius]
              ];
              const features = map.queryRenderedFeatures(hitBox, { layers });
              const settlementId = features[0] && features[0].properties && features[0].properties.id;
              if (settlementId) {
                console.debug('OsmapDigger settlement marker activated', settlementId);
                notifySettlementActivated(String(settlementId));
              }
            });
            map.on('load', async () => {
              try {
                const state = await fetch('/state.json', { cache: 'no-store' }).then(r => r.json());
                const hasSelection = state.selected && state.selected.features && state.selected.features.length > 0;
                applyState(state, hasSelection);
              } catch (error) {
                console.error('Could not load OsmapDigger map state', error);
              }
            });
          </script>
        </body>
        </html>
        """.trimIndent()

    companion object {
        private const val MAPLIBRE_VERSION = "5.13.0"
        private const val MAPLIBRE_JS_RESOURCE =
            "META-INF/resources/webjars/maplibre-gl/$MAPLIBRE_VERSION/dist/maplibre-gl.js"
        private const val MAPLIBRE_CSS_RESOURCE =
            "META-INF/resources/webjars/maplibre-gl/$MAPLIBRE_VERSION/dist/maplibre-gl.css"
        internal const val SETTLEMENT_INTERACTION_PATH = "/interaction/settlement"
        internal const val MAP_LOCATION_INTERACTION_PATH = "/interaction/location"
        internal const val MAX_INTERACTION_BODY_BYTES = 4096
        internal const val SETTLEMENT_INTERACTION_HIT_RADIUS_PX = 12
        private const val TILE_TYPE_MVT = 1
        private const val COMPRESSION_NONE = 1
        private const val COMPRESSION_GZIP = 2
        private val TILE_PATH = Regex("^/tiles/(\\d+)/(\\d+)/(\\d+)\\.pbf$")

        private fun emptyStateJson(): String =
            """{"results":{"type":"FeatureCollection","features":[]},"selected":{"type":"FeatureCollection","features":[]},"mapLocationPickingEnabled":false}"""

    }
}


private class ClientDisconnectedException(cause: IOException) : IOException(cause)

internal fun isExpectedClientDisconnect(failure: IOException): Boolean {
    var current: Throwable? = failure
    while (current != null) {
        val message = current.message?.lowercase().orEmpty()
        if (
            message.contains("broken pipe") ||
            message.contains("connection reset") ||
            message.contains("closed channel") ||
            message.contains("stream is closed")
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun decodeSettlementInteraction(body: String): String? =
    runCatching {
        Json.parseToJsonElement(body)
            .jsonObject["id"]
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

internal fun decodeMapLocationInteraction(body: String): GeoPoint? =
    runCatching {
        val objectValue = Json.parseToJsonElement(body).jsonObject
        val latitude = objectValue["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val longitude = objectValue["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
        if (
            latitude == null || longitude == null ||
            !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            null
        } else {
            GeoPoint(latitude = latitude, longitude = longitude)
        }
    }.getOrNull()
