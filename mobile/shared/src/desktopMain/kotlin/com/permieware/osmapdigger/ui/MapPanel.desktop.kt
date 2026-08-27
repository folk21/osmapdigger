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
import com.permieware.osmapdigger.map.MapOverlayGeoJson
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** Desktop MapLibre surface with a non-fatal fallback for unsupported native hosts. */
@Composable
internal actual fun MapPanel(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    styleJson: String?,
    results: List<Settlement>,
    selected: Settlement?,
) {
    if (!DesktopMapRuntime.isSupportedHost()) {
        DesktopMapFallback(modifier, datasetInfo, styleJson, results, selected)
        return
    }

    RenderMapLibrePanel(modifier, datasetInfo, styleJson, results, selected)
}

/**
 * Mirrors the native capabilities selected by the Desktop application Gradle configuration.
 * Keep this list aligned with the MapLibre Compose 0.13.x published Desktop runtimes.
 */
internal object DesktopMapRuntime {
    fun isSupportedHost(
        osName: String = System.getProperty("os.name"),
        osArch: String = System.getProperty("os.arch"),
    ): Boolean = capability(osName, osArch) != null

    internal fun capability(osName: String, osArch: String): String? {
        val os = osName.lowercase()
        val arch = osArch.lowercase()
        val normalizedArch =
            when (arch) {
                "x86_64", "amd64" -> "amd64"
                "aarch64", "arm64" -> "aarch64"
                else -> return null
            }

        return when {
            os == "mac os x" && normalizedArch == "aarch64" -> "macos-aarch64-metal"
            os.startsWith("linux") && normalizedArch == "amd64" -> "linux-amd64-opengl"
            os.startsWith("windows") && normalizedArch == "amd64" -> "windows-amd64-opengl"
            else -> null
        }
    }
}

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

    val resultJson = remember(results) { MapOverlayGeoJson.build(results) }
    val selectedJson = remember(selected) { MapOverlayGeoJson.build(listOfNotNull(selected)) }

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

@Composable
private fun DesktopMapFallback(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    styleJson: String?,
    results: List<Settlement>,
    selected: Settlement?,
) {
    Box(modifier.padding(24.dp)) {
        Card {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Map unavailable on this Desktop host", style = MaterialTheme.typography.titleMedium)
                Text(
                    "This OS/architecture has no MapLibre Compose native runtime configured by OsmapDigger. " +
                        "Search, filters, settlement details, and generated dataset data remain available.",
                )
                datasetInfo?.let {
                    Text("Dataset: ${it.displayName}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Results in current search: ${results.size}", style = MaterialTheme.typography.bodySmall)
                selected?.let {
                    Text(
                        "Selected: ${it.name} (${it.location.latitude}, ${it.location.longitude})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (styleJson == null) {
                    Text(
                        "The installed package also has no generated map artifact.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
