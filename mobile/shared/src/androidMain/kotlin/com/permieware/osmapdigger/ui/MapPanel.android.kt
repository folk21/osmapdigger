package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.map.ResultGeoJson
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** Android MapLibre implementation backed by the package's local PMTiles style. */
@Composable
internal actual fun MapPanel(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    styleJson: String?,
    results: List<Settlement>,
    selected: Settlement?,
) {
    RenderMapLibrePanel(modifier, datasetInfo, styleJson, results, selected)
}

/**
 * Renders the common map behavior for the Android target: local basemap, result markers,
 * selected settlement focus, and mandatory OpenStreetMap attribution.
 */
@Composable
private fun RenderMapLibrePanel(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    styleJson: String?,
    results: List<Settlement>,
    selected: Settlement?,
) {
    if (datasetInfo == null || styleJson == null) {
        Box(modifier.padding(24.dp)) {
            Card {
                Text(
                    if (datasetInfo == null) "Loading dataset…" else "This package has no generated map.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        return
    }

    val camera =
        rememberCameraState(
            firstPosition =
                CameraPosition(
                    target =
                        Position(
                            latitude = datasetInfo.center.latitude,
                            longitude = datasetInfo.center.longitude,
                        ),
                    zoom = datasetInfo.initialZoom,
                ),
        )

    LaunchedEffect(selected?.id) {
        selected?.let {
            camera.position =
                camera.position.copy(
                    target = Position(latitude = it.location.latitude, longitude = it.location.longitude),
                    zoom = maxOf(camera.position.zoom, 11.0),
                )
        }
    }

    val resultJson = remember(results) { ResultGeoJson.build(results) }
    val selectedJson = remember(selected) { ResultGeoJson.build(listOfNotNull(selected)) }

    Box(modifier) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Json(styleJson),
            cameraState = camera,
        ) {
            val resultSource = rememberGeoJsonSource(GeoJsonData.JsonString(resultJson))
            LaunchedEffect(resultJson) {
                resultSource.setData(GeoJsonData.JsonString(resultJson))
            }
            CircleLayer(
                id = "osmapdigger-results",
                source = resultSource,
                color = const(Color(0xFF2E7D32)),
                radius = const(5.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(1.dp),
            )

            if (selected != null) {
                val selectedSource = rememberGeoJsonSource(GeoJsonData.JsonString(selectedJson))
                LaunchedEffect(selectedJson) {
                    selectedSource.setData(GeoJsonData.JsonString(selectedJson))
                }
                CircleLayer(
                    id = "osmapdigger-selected",
                    source = selectedSource,
                    color = const(Color(0xFFD84315)),
                    radius = const(8.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(2.dp),
                )
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            Text(
                "© OpenStreetMap contributors",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
