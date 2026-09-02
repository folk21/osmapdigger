package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.workspace.AnalysisWorkspaceController
import com.permieware.osmapdigger.analysis.SettlementAnalysisDiagnostics
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.error.OperationalFailure
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.toOperationalFailure
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderRepository
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.map.MapPackage
import com.permieware.osmapdigger.notebook.FavoriteSettlementRepository
import com.permieware.osmapdigger.preferences.UserPreferencesRepository
import com.permieware.osmapdigger.presentation.SettlementDisplayNameResolver
import com.permieware.osmapdigger.presentation.UiLanguage
import com.permieware.osmapdigger.presentation.UiLocalization
import com.permieware.osmapdigger.presentation.UiStrings
import com.permieware.osmapdigger.runtime.OsmapDiggerRuntime
import com.permieware.osmapdigger.presentation.FilterSummaryBuilder
import com.permieware.osmapdigger.search.OptionalRadiusInput
import com.permieware.osmapdigger.search.SearchInputParser
import com.permieware.osmapdigger.search.SettlementSearchService
import com.permieware.osmapdigger.search.SettlementListImportResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Shared OsmapDigger application surface used by Android and Desktop hosts. */
@Composable
fun OsmapDiggerApp(
    runtime: OsmapDiggerRuntime?,
    userPreferences: UserPreferencesRepository,
    favoriteSettlements: FavoriteSettlementRepository,
    externalSearchProviders: ExternalSearchProviderRepository,
    onImportDataset: () -> Unit,
    onImportSettlementListFile: ((String) -> String?)? = null,
    hostFailure: OperationalFailure? = null,
    platformMapSurface: PlatformMapSurface? = null,
    presentationMode: AppPresentationMode = AppPresentationMode.RESPONSIVE,
    onAnalysisDiagnostics: (SettlementAnalysisDiagnostics) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var uiLanguageCode by rememberSaveable { mutableStateOf(UiLocalization.defaultLanguage.code) }
    val uiLanguage = UiLanguage.entries.firstOrNull { it.code == uiLanguageCode } ?: UiLocalization.defaultLanguage
    val strings = remember(uiLanguage) { UiLocalization.strings(uiLanguage) }

    CompositionLocalProvider(
        LocalUiLanguage provides uiLanguage,
        LocalUiStrings provides strings,
        LocalUiLanguageSetter provides { uiLanguageCode = it.code },
    ) {
        MaterialTheme {
        if (runtime == null) {
            MissingDatasetScreen(onImportDataset = onImportDataset, failure = hostFailure, modifier = modifier)
        } else {
            LoadedDatasetApp(
                runtime = runtime,
                userPreferences = userPreferences,
                favoriteSettlements = favoriteSettlements,
                externalSearchProviders = externalSearchProviders,
                onImportDataset = onImportDataset,
                onImportSettlementListFile = onImportSettlementListFile,
                platformMapSurface = platformMapSurface,
                presentationMode = presentationMode,
                onAnalysisDiagnostics = onAnalysisDiagnostics,
                hostFailure = hostFailure,
                modifier = modifier,
            )
        }
    }
    }
}

@Composable
private fun MissingDatasetScreen(
    onImportDataset: () -> Unit,
    failure: OperationalFailure?,
    modifier: Modifier,
) {
    val strings = LocalUiStrings.current
    Box(modifier.fillMaxSize().padding(32.dp)) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("OsmapDigger", style = MaterialTheme.typography.headlineMedium)
                Text(strings.noDatasetInstalled)
                Text(strings.generateAndImportDataset)
                failure?.let { Text(strings.operationalFailureMessage(it.kind), color = MaterialTheme.colorScheme.error) }
                LanguageSelector()
                Button(onClick = onImportDataset) {
                    Text(strings.importDataset)
                }
            }
        }
    }
}

/**
 * Coordinate shared dataset/filter/result state after platform adapters are available.
 * Wide Desktop layouts use side-by-side search/map panes; narrow Android layouts reuse
 * the same state through Search/Map tabs.
 */
@Composable
private fun LoadedDatasetApp(
    runtime: OsmapDiggerRuntime,
    userPreferences: UserPreferencesRepository,
    favoriteSettlements: FavoriteSettlementRepository,
    externalSearchProviders: ExternalSearchProviderRepository,
    onImportDataset: () -> Unit,
    onImportSettlementListFile: ((String) -> String?)?,
    platformMapSurface: PlatformMapSurface?,
    presentationMode: AppPresentationMode,
    onAnalysisDiagnostics: (SettlementAnalysisDiagnostics) -> Unit,
    hostFailure: OperationalFailure?,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val analysisController =
        remember(runtime.repository, userPreferences, favoriteSettlements, scope) {
            AnalysisWorkspaceController(
                repository = runtime.repository,
                userPreferences = userPreferences,
                scope = scope,
                favoriteSettlements = favoriteSettlements,
                onAnalysisDiagnostics = onAnalysisDiagnostics,
            )
        }
    val analysisState by analysisController.state.collectAsState()

    var selected by remember { mutableStateOf<SettlementDetails?>(null) }
    var mapFocusRequest by remember { mutableStateOf(0L) }
    var radiusText by remember { mutableStateOf("") }
    var radiusInitializedForDataset by remember { mutableStateOf<String?>(null) }
    var uiError by remember { mutableStateOf<String?>(null) }
    var uiFailure by remember { mutableStateOf<OperationalFailure?>(null) }
    var searchProviders by remember { mutableStateOf<List<ExternalSearchProvider>>(emptyList()) }
    val strings = LocalUiStrings.current
    val uiLanguage = LocalUiLanguage.current
    var settlementNameEntries by remember(runtime.repository) { mutableStateOf<List<SettlementSearchEntry>>(emptyList()) }

    LaunchedEffect(runtime.repository) {
        try {
            settlementNameEntries = runtime.repository.settlementSearchEntries()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            uiFailure = failure.toOperationalFailure(OperationalFailureKind.DATABASE, "Could not load settlement search index")
        }
    }

    val settlementDisplayNames = remember(settlementNameEntries, uiLanguage) {
        SettlementDisplayNameResolver.resolve(settlementNameEntries, uiLanguage.code)
    }
    val settlementDisplayName: (Settlement) -> String = { settlement ->
        settlementDisplayNames[settlement.id] ?: settlement.name
    }
    val settlementsById = remember(settlementNameEntries) {
        settlementNameEntries.associate { it.settlement.id to it.settlement }
    }

    LaunchedEffect(analysisController) {
        radiusInitializedForDataset = null
        selected = null
        radiusText = ""
        uiError = null
        uiFailure = null
        searchProviders = emptyList()
        analysisController.initialize()
    }

    LaunchedEffect(analysisState.initialized, analysisState.datasetInfo?.id) {
        val info = analysisState.datasetInfo ?: return@LaunchedEffect
        if (analysisState.initialized && radiusInitializedForDataset != info.id) {
            radiusText = analysisState.radiusKm?.toString() ?: ""
            radiusInitializedForDataset = info.id
        }
    }

    LaunchedEffect(analysisState.datasetInfo?.id, analysisState.datasetInfo?.countryCode, externalSearchProviders) {
        val info = analysisState.datasetInfo ?: return@LaunchedEffect
        try {
            searchProviders = externalSearchProviders.providersFor(info.countryCode)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            uiFailure = failure.toOperationalFailure(OperationalFailureKind.SETTINGS, "Could not load external search providers")
        }
    }

    val datasetInfo = analysisState.datasetInfo
    val definitions = analysisState.definitions
    val conditions = analysisState.conditions
    val center = analysisState.center
    val rankedResults = analysisState.rankedResults
    val results = rankedResults.map { it.settlement }
    val definitionMap = remember(definitions) { definitions.associateBy { it.id } }
    val radiusInput = SearchInputParser.optionalRadiusKm(radiusText)
    val radiusError =
        when {
            radiusInput is OptionalRadiusInput.Unset -> null
            center == null -> strings.radiusRequiresCenter
            radiusInput is OptionalRadiusInput.Invalid -> strings.radiusInvalid
            else -> null
        }
    val request =
        SearchRequest(
            center = center,
            radiusKm = (radiusInput as? OptionalRadiusInput.Value)?.kilometers,
            conditions = conditions,
        )
    val summary = FilterSummaryBuilder.build(request, definitionMap, strings.filterSummaryText())
    val error = uiError ?: hostFailure?.let { strings.operationalFailureMessage(it.kind) } ?: uiFailure?.let { strings.operationalFailureMessage(it.kind) } ?: analysisState.failure?.let { strings.operationalFailureMessage(it.kind) }
    val updateExternalSearchSettings: (List<ExternalSearchProviderTermsUpdate>) -> Unit = { updates ->
        val info = analysisState.datasetInfo
        if (info != null) {
            scope.launch {
                try {
                    externalSearchProviders.updateQueryTerms(updates)
                    searchProviders = externalSearchProviders.providersFor(info.countryCode)
                    uiFailure = null
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    uiFailure = failure.toOperationalFailure(OperationalFailureKind.SETTINGS, "Could not update external search provider terms")
                }
            }
        }
    }

    LaunchedEffect(results.map { it.id }) {
        val selectedId = selected?.settlement?.id ?: return@LaunchedEffect
        if (results.none { it.id == selectedId }) {
            selected = null
        }
    }

    val performSearch: () -> Unit = {
        if (radiusError != null) {
            uiError = radiusError
        } else {
            uiError = null
            analysisController.refreshNow()
        }
    }

    val selectSettlement: (Settlement) -> Unit = { settlement ->
        if (selected?.settlement?.id == settlement.id) {
            mapFocusRequest += 1L
        } else {
            scope.launch {
                try {
                    selected = runtime.repository.details(settlement.id)
                    mapFocusRequest += 1L
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    uiFailure = failure.toOperationalFailure(OperationalFailureKind.DATABASE, "Could not load settlement details")
                }
            }
        }
    }

    val settlementSearch = remember(runtime.repository) { SettlementSearchService(runtime.repository) }
    val settlementImportResolver = remember(runtime.repository) { SettlementListImportResolver(runtime.repository) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp

        val searchPane: @Composable (Modifier) -> Unit = { paneModifier ->
            SearchPane(
                modifier = paneModifier,
                datasetInfo = datasetInfo,
                definitions = definitions,
                conditions = conditions,
                onConditionsChanged = { analysisController.updateConditions(it) },
                center = center,
                onCenterChanged = {
                    analysisController.updateCenter(it)
                    if (it == null) {
                        radiusText = ""
                    }
                },
                radiusText = radiusText,
                onRadiusChanged = { value ->
                    radiusText = value
                    uiError = null
                    when (val parsed = SearchInputParser.optionalRadiusKm(value)) {
                        OptionalRadiusInput.Unset -> analysisController.updateRadiusKm(null)
                        OptionalRadiusInput.Invalid -> Unit
                        is OptionalRadiusInput.Value ->
                            if (center != null) analysisController.updateRadiusKm(parsed.kilometers)
                    }
                },
                radiusError = radiusError,
                results = results,
                selected = selected,
                summary = summary,
                running = analysisState.analyzing,
                error = error,
                settlementSearch = settlementSearch,
                onSearch = performSearch,
                onSelect = selectSettlement,
                onImportDataset = onImportDataset,
                externalLinks = runtime.externalLinks,
                searchProviders = searchProviders,
                onExternalSearchSettingsSave = updateExternalSearchSettings,
                settlementDisplayName = settlementDisplayName,
            )
        }

        if (wide && presentationMode == AppPresentationMode.DESKTOP_ANALYSIS) {
            DesktopAnalysisWorkspace(
                modifier = Modifier.fillMaxSize(),
                datasetInfo = datasetInfo,
                definitions = definitions,
                conditions = conditions,
                onConditionsChanged = { analysisController.updateConditions(it) },
                effectivePreferences = analysisState.effectivePreferences,
                preferenceOverrides = analysisState.preferenceOverrides,
                onPreferenceEnabledChanged = analysisController::updatePreferenceEnabled,
                onPreferenceWeightChanged = analysisController::updatePreferenceWeight,
                onPreferenceThresholdsChanged = analysisController::updatePreferenceThresholds,
                onPreferenceReset = analysisController::resetPreference,
                center = center,
                onCenterChanged = {
                    analysisController.updateCenter(it)
                    if (it == null) {
                        radiusText = ""
                    }
                },
                radiusText = radiusText,
                activeRadiusKm = analysisState.radiusKm,
                onRadiusChanged = { value ->
                    radiusText = value
                    uiError = null
                    when (val parsed = SearchInputParser.optionalRadiusKm(value)) {
                        OptionalRadiusInput.Unset -> analysisController.updateRadiusKm(null)
                        OptionalRadiusInput.Invalid -> Unit
                        is OptionalRadiusInput.Value ->
                            if (center != null) analysisController.updateRadiusKm(parsed.kilometers)
                    }
                },
                radiusError = radiusError,
                summary = summary,
                rankedResults = rankedResults,
                rankedCandidatesBySettlementId = analysisState.rankedCandidatesBySettlementId,
                favorites = analysisState.favorites,
                favoriteSettlementsById = settlementsById,
                onFavoriteAdded = analysisController::addFavorite,
                onFavoriteRemoved = analysisController::removeFavorite,
                onFavoritesCleared = analysisController::clearFavorites,
                onFavoriteNoteChanged = analysisController::updateFavoriteNote,
                onFavoriteSnapshotUpdated = analysisController::updateFavoriteSnapshot,
                candidateScope = analysisState.candidateScope,
                importedCandidateList = analysisState.importedCandidateList,
                onImportedCandidatesApplied = analysisController::applyImportedCandidates,
                onImportedCandidatesActivated = analysisController::activateImportedCandidates,
                onImportedCandidatesDeactivated = analysisController::deactivateImportedCandidates,
                onImportedCandidatesCleared = analysisController::clearImportedCandidates,
                settlementImportResolver = settlementImportResolver,
                onImportSettlementListFile = onImportSettlementListFile,
                selected = selected,
                running = analysisState.analyzing,
                error = error,
                settlementSearch = settlementSearch,
                onSelect = selectSettlement,
                onCloseSelected = { selected = null },
                onImportDataset = onImportDataset,
                externalLinks = runtime.externalLinks,
                searchProviders = searchProviders,
                onExternalSearchSettingsSave = updateExternalSearchSettings,
                settlementDisplayName = settlementDisplayName,
                mapContent = { mapModifier, onSettlementActivated, onMapLocationActivated ->
                    RuntimeMapPanel(
                        modifier = mapModifier,
                        datasetInfo = datasetInfo,
                        mapPackage = runtime.mapPackage,
                        results = results.map { it.copy(name = settlementDisplayName(it)) },
                        selected = selected?.settlement?.let { it.copy(name = settlementDisplayName(it)) },
                        focusRequest = mapFocusRequest,
                        platformMapSurface = platformMapSurface,
                        onSettlementActivated = onSettlementActivated,
                        onMapLocationActivated = onMapLocationActivated,
                    )
                },
            )
        } else if (wide) {
            Row(Modifier.fillMaxSize()) {
                searchPane(Modifier.width(430.dp).fillMaxHeight())
                RuntimeMapPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    datasetInfo = datasetInfo,
                    mapPackage = runtime.mapPackage,
                    results = results.map { it.copy(name = settlementDisplayName(it)) },
                    selected = selected?.settlement?.let { it.copy(name = settlementDisplayName(it)) },
                    platformMapSurface = platformMapSurface,
                )
            }
        } else {
            var page by remember { mutableStateOf(0) }
            Column(Modifier.fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = page) {
                    Tab(selected = page == 0, onClick = { page = 0 }, text = { Text(strings.searchTab) })
                    Tab(selected = page == 1, onClick = { page = 1 }, text = { Text(strings.mapTab) })
                }
                if (page == 0) {
                    searchPane(Modifier.fillMaxSize())
                } else {
                    RuntimeMapPanel(
                        modifier = Modifier.fillMaxSize(),
                        datasetInfo = datasetInfo,
                        mapPackage = runtime.mapPackage,
                        results = results.map { it.copy(name = settlementDisplayName(it)) },
                        selected = selected?.settlement?.let { it.copy(name = settlementDisplayName(it)) },
                        platformMapSurface = platformMapSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeMapPanel(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    mapPackage: MapPackage,
    results: List<Settlement>,
    selected: Settlement?,
    focusRequest: Long = 0L,
    platformMapSurface: PlatformMapSurface?,
    onSettlementActivated: ((String) -> Unit)? = null,
    onMapLocationActivated: ((GeoPoint) -> Unit)? = null,
) {
    if (platformMapSurface != null) {
        platformMapSurface.Render(
            modifier = modifier,
            datasetInfo = datasetInfo,
            mapPackage = mapPackage,
            results = results,
            selected = selected,
            focusRequest = focusRequest,
            onSettlementActivated = onSettlementActivated,
            onMapLocationActivated = onMapLocationActivated,
        )
    } else {
        MapPanel(
            modifier = modifier,
            datasetInfo = datasetInfo,
            styleJson = mapPackage.styleJson,
            results = results,
            selected = selected,
            focusRequest = focusRequest,
            onSettlementActivated = onSettlementActivated,
            onMapLocationActivated = onMapLocationActivated,
        )
    }
}

