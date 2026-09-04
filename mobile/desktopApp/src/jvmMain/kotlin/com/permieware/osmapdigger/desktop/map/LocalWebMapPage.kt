package com.permieware.osmapdigger.desktop.map

import com.permieware.osmapdigger.domain.DatasetInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Builds the same-origin style and HTML page served to the packaged Intel macOS MapLibre GL JS renderer. */
internal object LocalWebMapPage {
    private const val MAPLIBRE_VERSION = "5.13.0"
    const val MAPLIBRE_JS_RESOURCE =
        "META-INF/resources/webjars/maplibre-gl/$MAPLIBRE_VERSION/dist/maplibre-gl.js"
    const val MAPLIBRE_CSS_RESOURCE =
        "META-INF/resources/webjars/maplibre-gl/$MAPLIBRE_VERSION/dist/maplibre-gl.css"

    fun rewriteStyle(styleJson: String, baseUrl: String, minZoom: Int, maxZoom: Int): String {
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
                            put("minzoom", minZoom)
                            put("maxzoom", maxZoom)
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

    fun indexHtml(datasetInfo: DatasetInfo): String =
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
                await fetch('${LocalWebMapInteraction.SETTLEMENT_PATH}', {
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
                await fetch('${LocalWebMapInteraction.MAP_LOCATION_PATH}', {
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
              const hitRadius = ${LocalWebMapInteraction.SETTLEMENT_HIT_RADIUS_PX};
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

}
