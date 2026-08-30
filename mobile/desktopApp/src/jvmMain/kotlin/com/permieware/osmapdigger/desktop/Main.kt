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
import com.permieware.osmapdigger.desktop.importing.DesktopSettlementListFilePicker
import com.permieware.osmapdigger.desktop.preferences.SqliteUserPreferencesRepository
import com.permieware.osmapdigger.desktop.runtime.DesktopConfigLoader
import com.permieware.osmapdigger.desktop.runtime.DesktopDataset
import com.permieware.osmapdigger.desktop.runtime.DesktopDatasetChooser
import com.permieware.osmapdigger.desktop.settings.SqliteExternalSearchProviderRepository
import com.permieware.osmapdigger.error.OperationalFailure
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.toOperationalFailure
import com.permieware.osmapdigger.external.OperationalExternalSearchProviderRepository
import com.permieware.osmapdigger.preferences.OperationalUserPreferencesRepository
import com.permieware.osmapdigger.ui.AppPresentationMode
import com.permieware.osmapdigger.ui.OsmapDiggerApp
import java.nio.file.Paths

private data class DesktopDatasetLoad(
    val dataset: DesktopDataset? = null,
    val failure: OperationalFailure? = null,
)

/**
 * Load the configured Desktop dataset while keeping "not configured" distinct from an operational failure.
 * Startup never exposes raw exception text to Compose; technical causes remain in Desktop diagnostics.
 */
private fun loadConfiguredDataset(): DesktopDatasetLoad {
    System.getenv("OSMAPDIGGER_DATASET_DIR")
        ?.takeIf { it.isNotBlank() }
        ?.let { path ->
            DesktopDiagnostics.info("dataset.config", "Using OSMAPDIGGER_DATASET_DIR=$path")
            return try {
                DesktopDatasetLoad(
                    dataset =
                        DesktopDiagnostics.measure("dataset.open.explicit") {
                            DesktopDataset.open(Paths.get(path))
                        },
                )
            } catch (failure: Throwable) {
                DesktopDiagnostics.error("dataset.open.explicit", "Could not open explicit dataset", failure)
                DesktopDatasetLoad(
                    failure = failure.toOperationalFailure(OperationalFailureKind.DATASET_STORAGE, "Could not open explicit dataset"),
                )
            }
        }

    val config =
        try {
            DesktopConfigLoader.load()
        } catch (failure: Throwable) {
            DesktopDiagnostics.error("desktop.config", "Could not load Desktop config", failure)
            return DesktopDatasetLoad(
                failure = failure.toOperationalFailure(OperationalFailureKind.SETTINGS, "Could not load Desktop config"),
            )
        } ?: return DesktopDatasetLoad()
    val packagePath = DesktopConfigLoader.resolvePackage(config)
    val absolutePackage = packagePath.toAbsolutePath()
    val packageExists = java.nio.file.Files.exists(packagePath)
    val packageSize =
        if (packageExists && java.nio.file.Files.isRegularFile(packagePath)) {
            // File size is diagnostics only and does not affect package loading semantics.
            runCatching { java.nio.file.Files.size(packagePath) }.getOrNull()
        } else {
            null
        }
    DesktopDiagnostics.info(
        "dataset.config",
        "package=$absolutePackage exists=$packageExists sizeBytes=${packageSize ?: "unknown"}",
    )

    return try {
        DesktopDatasetLoad(
            dataset =
                DesktopDiagnostics.measure("dataset.install-open") {
                    DesktopDataset.installAndOpen(packagePath)
                },
        )
    } catch (failure: Throwable) {
        DesktopDiagnostics.error("dataset.install-open", "Dataset installation/open failed", failure)
        DesktopDatasetLoad(
            failure = failure.toOperationalFailure(OperationalFailureKind.DATASET_STORAGE, "Dataset installation/open failed"),
        )
    }
}

/** Desktop development/product host. */
fun main() {
    DesktopDiagnostics.start()
    val configured = loadConfiguredDataset()

    application {
        val datasetState = remember { mutableStateOf(configured.dataset) }
        val hostFailure = remember { mutableStateOf(configured.failure) }
        val userPreferences =
            remember {
                OperationalUserPreferencesRepository(SqliteUserPreferencesRepository.createDefault())
            }
        val externalSearchProviders =
            remember {
                OperationalExternalSearchProviderRepository(SqliteExternalSearchProviderRepository.createDefault())
            }
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
                hostFailure = hostFailure.value,
                platformMapSurface = platformMapSurface,
                presentationMode = AppPresentationMode.DESKTOP_ANALYSIS,
                onAnalysisDiagnostics = { diagnostics ->
                    DesktopDiagnostics.info(
                        "analysis.performance",
                        buildString {
                            append("candidates=")
                            append(diagnostics.candidateCount)
                            append(" exactEligible=")
                            append(diagnostics.exactEligibleCandidateCount)
                            append(" scoringMetrics=")
                            append(diagnostics.enabledScoringMetricCount)
                            append(" results=")
                            append(diagnostics.resultCount)
                            append(" retrievalMs=")
                            append("%.3f".format(java.util.Locale.ROOT, diagnostics.batchRetrievalMillis))
                            append(" sharedMs=")
                            append("%.3f".format(java.util.Locale.ROOT, diagnostics.sharedScoringSortMillis))
                            append(" totalMs=")
                            append("%.3f".format(java.util.Locale.ROOT, diagnostics.totalMillis))
                        },
                    )
                },
                onImportSettlementListFile = DesktopSettlementListFilePicker::chooseAndRead,
                onImportDataset = {
                    try {
                        DesktopDatasetChooser.chooseAndOpen()?.let { opened ->
                            val previous = datasetState.value
                            datasetState.value = opened
                            hostFailure.value = null
                            if (previous !== opened) previous?.close()
                        }
                    } catch (failure: Throwable) {
                        DesktopDiagnostics.error("dataset.import", "Dataset import failed", failure)
                        hostFailure.value =
                            failure.toOperationalFailure(OperationalFailureKind.DATASET_STORAGE, "Dataset import failed")
                    }
                },
            )
        }
    }
}
