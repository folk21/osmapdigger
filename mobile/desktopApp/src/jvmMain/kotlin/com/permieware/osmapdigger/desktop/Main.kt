package com.permieware.osmapdigger.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.permieware.osmapdigger.desktop.diagnostics.DesktopDiagnostics
import com.permieware.osmapdigger.desktop.map.IntelMacWebMapSurface
import com.permieware.osmapdigger.desktop.preferences.SqliteUserPreferencesRepository
import com.permieware.osmapdigger.desktop.runtime.DesktopDataset
import com.permieware.osmapdigger.desktop.settings.SqliteExternalSearchProviderRepository
import com.permieware.osmapdigger.desktop.runtime.DesktopDatasetChooser
import com.permieware.osmapdigger.desktop.runtime.DesktopConfigLoader
import com.permieware.osmapdigger.ui.OsmapDiggerApp
import java.nio.file.Paths


/**
 * Loads the default Desktop dataset from configuration.
 *
 * Startup order:
 * 1. explicit OSMAPDIGGER_DATASET_DIR for development;
 * 2. config/desktop-config.json portable package;
 * 3. no dataset.
 */
private fun loadConfiguredDataset(): DesktopDataset? {
    System.getenv("OSMAPDIGGER_DATASET_DIR")
        ?.takeIf { it.isNotBlank() }
        ?.let { path ->
            DesktopDiagnostics.info("dataset.config", "Using OSMAPDIGGER_DATASET_DIR=$path")
            return runCatching {
                DesktopDiagnostics.measure("dataset.open.explicit") {
                    DesktopDataset.open(Paths.get(path))
                }
            }.onFailure { failure ->
                DesktopDiagnostics.error("dataset.open.explicit", "Could not open explicit dataset", failure)
            }.getOrNull()
        }

    val config =
        DesktopConfigLoader.load()
            ?: return null

    val packagePath = DesktopConfigLoader.resolvePackage(config)

    val absolutePackage = packagePath.toAbsolutePath()
    val packageExists = java.nio.file.Files.exists(packagePath)
    val packageSize =
        if (packageExists && java.nio.file.Files.isRegularFile(packagePath)) {
            runCatching { java.nio.file.Files.size(packagePath) }.getOrNull()
        } else {
            null
        }
    DesktopDiagnostics.info(
        "dataset.config",
        "package=$absolutePackage exists=$packageExists sizeBytes=${packageSize ?: "unknown"}",
    )

    return runCatching {
        DesktopDiagnostics.measure("dataset.install-open") {
            DesktopDataset.installAndOpen(packagePath)
        }
    }.onFailure { failure ->
        DesktopDiagnostics.error("dataset.install-open", "Dataset installation/open failed", failure)
    }.getOrNull()
}

/** Desktop development/product host. */
fun main() {
    DesktopDiagnostics.start()
    val configured = loadConfiguredDataset()

    application {
        val datasetState = remember { mutableStateOf(configured) }
        val userPreferences = remember { SqliteUserPreferencesRepository.createDefault() }
        val externalSearchProviders = remember { SqliteExternalSearchProviderRepository.createDefault() }
        val platformMapSurface =
            remember {
                IntelMacWebMapSurface.createIfSupported().also { surface ->
                    DesktopDiagnostics.info(
                        "map.renderer",
                        if (surface != null) {
                            "Selected Intel macOS JCEF renderer"
                        } else {
                            "No Desktop-host renderer override; shared Desktop map renderer/fallback owns rendering"
                        },
                    )
                }
            }

        // The effect belongs to the application lifetime, not to a particular dataset.
        // When the application is disposed, close whichever dataset is active at that time.
        DisposableEffect(Unit) {
            onDispose {
                DesktopDiagnostics.info("shutdown", "Closing Desktop resources")
                platformMapSurface?.close()
                datasetState.value?.close()
                DesktopDiagnostics.info("shutdown", "Desktop resources closed")
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1400.dp, height = 900.dp),
            title = "OsmapDigger",
        ) {
            OsmapDiggerApp(
                runtime = datasetState.value?.runtime,
                userPreferences = userPreferences,
                externalSearchProviders = externalSearchProviders,
                platformMapSurface = platformMapSurface,
                onImportDataset = {
                    DesktopDatasetChooser.chooseAndOpen()?.let { opened ->
                        val previous = datasetState.value
                        datasetState.value = opened

                        // Close only the dataset that has actually been replaced.
                        // Closing through DisposableEffect(dataset) is incorrect because
                        // its onDispose callback can observe the newly assigned state value.
                        if (previous !== opened) {
                            previous?.close()
                        }
                    }
                },
            )
        }
    }
}
