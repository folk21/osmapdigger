package com.permieware.osmapdigger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.permieware.osmapdigger.runtime.AndroidDataset
import com.permieware.osmapdigger.runtime.AndroidDatasetInstaller
import com.permieware.osmapdigger.ui.OsmapDiggerApp

/** Android host with local dataset ZIP import. */
class MainActivity : ComponentActivity() {
    private var datasetState: MutableState<AndroidDataset?>? = null

    private val datasetPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    val directory = AndroidDatasetInstaller.install(this, uri)
                    AndroidDataset.open(this, directory)
                }.onSuccess { opened ->
                    datasetState?.value?.close()
                    datasetState?.value = opened
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initial =
            AndroidDataset.currentDirectory(this)
                .takeIf { it.isDirectory }
                ?.let { runCatching { AndroidDataset.open(this, it) }.getOrNull() }

        setContent {
            val state = remember { mutableStateOf(initial) }
            datasetState = state

            OsmapDiggerApp(
                runtime = state.value?.runtime,
                onImportDataset = {
                    datasetPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                },
            )
        }
    }

    override fun onDestroy() {
        datasetState?.value?.close()
        datasetState = null
        super.onDestroy()
    }
}
