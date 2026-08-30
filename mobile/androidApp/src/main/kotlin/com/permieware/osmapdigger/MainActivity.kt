package com.permieware.osmapdigger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.permieware.osmapdigger.error.OperationalFailure
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.toOperationalFailure
import com.permieware.osmapdigger.external.OperationalExternalSearchProviderRepository
import com.permieware.osmapdigger.notebook.AndroidFavoriteSettlementRepository
import com.permieware.osmapdigger.notebook.OperationalFavoriteSettlementRepository
import com.permieware.osmapdigger.preferences.AndroidUserPreferencesRepository
import com.permieware.osmapdigger.preferences.OperationalUserPreferencesRepository
import com.permieware.osmapdigger.runtime.AndroidDataset
import com.permieware.osmapdigger.runtime.AndroidDatasetInstaller
import com.permieware.osmapdigger.settings.AndroidExternalSearchProviderRepository
import com.permieware.osmapdigger.ui.OsmapDiggerApp

/** Android host with failure-safe local dataset ZIP import. */
class MainActivity : ComponentActivity() {
    private var datasetState: MutableState<AndroidDataset?>? = null
    private var failureState: MutableState<OperationalFailure?>? = null

    private val datasetPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    val directory = AndroidDatasetInstaller.install(this, uri)
                    val opened = AndroidDataset.open(this, directory)
                    val previous = datasetState?.value
                    datasetState?.value = opened
                    failureState?.value = null
                    if (previous !== opened) previous?.close()
                } catch (failure: Throwable) {
                    failureState?.value =
                        failure.toOperationalFailure(
                            OperationalFailureKind.DATASET_STORAGE,
                            "Dataset import failed",
                        )
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var initialFailure: OperationalFailure? = null
        val initial =
            AndroidDataset.currentDirectory(this)
                .takeIf { it.isDirectory }
                ?.let { directory ->
                    try {
                        AndroidDataset.open(this, directory)
                    } catch (failure: Throwable) {
                        initialFailure =
                            failure.toOperationalFailure(
                                OperationalFailureKind.DATASET_STORAGE,
                                "Could not open installed dataset",
                            )
                        null
                    }
                }

        val userPreferences =
            OperationalUserPreferencesRepository(AndroidUserPreferencesRepository(this))
        val favoriteSettlements =
            OperationalFavoriteSettlementRepository(AndroidFavoriteSettlementRepository(this))
        val externalSearchProviders =
            OperationalExternalSearchProviderRepository(AndroidExternalSearchProviderRepository(this))

        setContent {
            val state = remember { mutableStateOf(initial) }
            val failures = remember { mutableStateOf(initialFailure) }
            datasetState = state
            failureState = failures

            OsmapDiggerApp(
                runtime = state.value?.runtime,
                userPreferences = userPreferences,
                favoriteSettlements = favoriteSettlements,
                externalSearchProviders = externalSearchProviders,
                hostFailure = failures.value,
                onImportDataset = {
                    datasetPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                },
            )
        }
    }

    override fun onDestroy() {
        datasetState?.value?.close()
        datasetState = null
        failureState = null
        super.onDestroy()
    }
}
