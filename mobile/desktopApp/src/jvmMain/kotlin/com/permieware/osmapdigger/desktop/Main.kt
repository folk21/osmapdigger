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
import com.permieware.osmapdigger.ui.OsmapDiggerApp
import java.nio.file.Paths

/** Desktop development/product host. */
fun main() {
    val configured =
        System.getenv("OSMAPDIGGER_DATASET_DIR")
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { DesktopDataset.open(Paths.get(it)) }.getOrNull() }

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
