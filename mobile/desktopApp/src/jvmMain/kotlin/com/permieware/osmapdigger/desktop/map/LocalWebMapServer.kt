package com.permieware.osmapdigger.desktop.map

import com.permieware.osmapdigger.desktop.diagnostics.DesktopDiagnostics
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.map.MapOverlayGeoJson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
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
 * Loopback-only HTTP lifecycle and router for the Intel macOS JCEF renderer.
 *
 * PMTiles access, page/style construction, and browser interaction payload semantics are delegated
 * to focused owners. This class only coordinates the local server lifecycle, request routing,
 * response transport, counters, and host callbacks.
 */
internal class LocalWebMapServer(
    private val datasetInfo: DatasetInfo,
    styleJson: String,
    pmtilesPath: Path,
    private val onSettlementActivated: (String) -> Unit = {},
    private val onMapLocationActivated: (GeoPoint) -> Unit = {},
) : Closeable {
    private val tileSource = LocalPmtilesTileSource(pmtilesPath)
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
    private val baseUrl = "http://127.0.0.1:${server.address.port}"
    private val rewrittenStyle =
        LocalWebMapPage.rewriteStyle(
            styleJson = styleJson,
            baseUrl = baseUrl,
            minZoom = tileSource.minZoom,
            maxZoom = tileSource.maxZoom,
        )

    val indexUrl: String
        get() = "$baseUrl/index.html"

    init {
        server.createContext("/", ::handle)
        server.start()
        DesktopDiagnostics.info(
            "map.server",
            "started dataset=${datasetInfo.id} address=$baseUrl pmtiles=${pmtilesPath.toAbsolutePath()} sizeBytes=${Files.size(pmtilesPath)} minZoom=${tileSource.minZoom} maxZoom=${tileSource.maxZoom} compression=${tileSource.compressionCode}",
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
        runCatching { tileSource.close() }
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
            if (path == LocalWebMapInteraction.SETTLEMENT_PATH) {
                handleSettlementInteraction(exchange)
                return
            }
            if (path == LocalWebMapInteraction.MAP_LOCATION_PATH) {
                handleMapLocationInteraction(exchange)
                return
            }
            if (exchange.requestMethod != "GET") {
                send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".toByteArray())
                return
            }

            when (path) {
                "/", "/index.html" -> sendText(exchange, "text/html; charset=utf-8", LocalWebMapPage.indexHtml(datasetInfo))
                "/style.json" -> sendText(exchange, "application/json; charset=utf-8", rewrittenStyle)
                "/state.json" -> {
                    exchange.responseHeaders.set("Cache-Control", "no-store")
                    sendText(exchange, "application/json; charset=utf-8", stateJson.get())
                }
                "/maplibre-gl.js" ->
                    sendClasspathAsset(
                        exchange,
                        LocalWebMapPage.MAPLIBRE_JS_RESOURCE,
                        "text/javascript; charset=utf-8",
                    )
                "/maplibre-gl.css" ->
                    sendClasspathAsset(
                        exchange,
                        LocalWebMapPage.MAPLIBRE_CSS_RESOURCE,
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

        val body = exchange.requestBody.readNBytes(LocalWebMapInteraction.MAX_BODY_BYTES + 1)
        if (body.size > LocalWebMapInteraction.MAX_BODY_BYTES) {
            send(exchange, 413, "text/plain; charset=utf-8", "Interaction payload too large".toByteArray())
            return
        }
        val settlementId =
            LocalWebMapInteraction.decodeSettlement(body.toString(StandardCharsets.UTF_8))
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

        val body = exchange.requestBody.readNBytes(LocalWebMapInteraction.MAX_BODY_BYTES + 1)
        if (body.size > LocalWebMapInteraction.MAX_BODY_BYTES) {
            send(exchange, 413, "text/plain; charset=utf-8", "Interaction payload too large".toByteArray())
            return
        }
        val location =
            LocalWebMapInteraction.decodeMapLocation(body.toString(StandardCharsets.UTF_8))
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

    private fun sendTile(exchange: HttpExchange, zoom: Int, x: Int, y: Int) {
        val tile = tileSource.getTile(zoom, x, y)
        if (tile == null) {
            send(exchange, 404, "text/plain; charset=utf-8", ByteArray(0))
            return
        }

        exchange.responseHeaders.set("Cache-Control", "public, max-age=3600")
        if (tileSource.isGzipCompressed) {
            exchange.responseHeaders.set("Content-Encoding", "gzip")
        }
        send(exchange, 200, "application/vnd.mapbox-vector-tile", tile)
    }

    private fun sendClasspathAsset(exchange: HttpExchange, resource: String, contentType: String) {
        val bytes =
            javaClass.classLoader.getResourceAsStream(resource)?.use { it.readBytes() }
                ?: error("Required WebJar asset is missing: $resource")
        exchange.responseHeaders.set("Cache-Control", "public, max-age=86400")
        send(exchange, 200, contentType, bytes)
    }

    private fun sendText(exchange: HttpExchange, contentType: String, text: String) {
        send(exchange, 200, contentType, text.toByteArray(StandardCharsets.UTF_8))
    }

    private fun send(exchange: HttpExchange, status: Int, contentType: String, bytes: ByteArray) {
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

    private companion object {
        val TILE_PATH = Regex("^/tiles/(\\d+)/(\\d+)/(\\d+)\\.pbf$")

        fun emptyStateJson(): String =
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
