package com.permieware.osmapdigger.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.permieware.osmapdigger.desktop.runtime.DesktopDataset
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
            return runCatching { DesktopDataset.open(Paths.get(path)) }.getOrNull()
        }

    val config =
        DesktopConfigLoader.load()
            ?: return null

    val packagePath = DesktopConfigLoader.resolvePackage(config)

    println("Dataset package resolved: $packagePath")
    println("Dataset package absolute: ${packagePath.toAbsolutePath()}")
    println("Dataset package exists: ${java.nio.file.Files.exists(packagePath)}")

    return runCatching {
        println("Starting dataset installation")

        val dataset = DesktopDataset.installAndOpen(packagePath)

        println("Dataset installation completed")

        dataset
    }.onFailure {
        println("Dataset installation failed: ${it.message}")
        it.printStackTrace()
    }.getOrNull()
}

/** Desktop development/product host. */
fun main() {
    val configured =
        loadConfiguredDataset()

    application {
        val datasetState = remember { mutableStateOf(configured) }

        // The effect belongs to the application lifetime, not to a particular dataset.
        // When the application is disposed, close whichever dataset is active at that time.
        DisposableEffect(Unit) {
            onDispose {
                datasetState.value?.close()
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1400.dp, height = 900.dp),
            title = "OsmapDigger",
        ) {
            OsmapDiggerApp(
                runtime = datasetState.value?.runtime,
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
