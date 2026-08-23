package com.permieware.osmapdigger.desktop

import androidx.compose.runtime.*
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
        var dataset by remember { mutableStateOf(configured) }

        DisposableEffect(dataset) {
            onDispose {
                if (dataset !== configured) {
                    dataset?.close()
                }
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1400.dp, height = 900.dp),
            title = "OsmapDigger",
        ) {
            OsmapDiggerApp(
                runtime = dataset?.runtime,
                onImportDataset = {
                    DesktopDatasetChooser.chooseAndOpen()?.let { opened ->
                        dataset?.takeIf { it !== configured }?.close()
                        dataset = opened
                    }
                },
            )
        }
    }
}
