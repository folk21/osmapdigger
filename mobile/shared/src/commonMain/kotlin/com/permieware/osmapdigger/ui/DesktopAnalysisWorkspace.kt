package com.permieware.osmapdigger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.analysis.ImportedCandidateList
import com.permieware.osmapdigger.analysis.RankedSettlementResult
import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.analysis.SettlementCandidateScope
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.external.ExternalLinkOpener
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.notebook.FavoriteNotebookExporter
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference
import com.permieware.osmapdigger.preferences.MetricPreferenceOverride
import com.permieware.osmapdigger.presentation.DesktopWorkspaceLayoutPolicy
import com.permieware.osmapdigger.search.SettlementListImportResolver
import com.permieware.osmapdigger.search.SettlementSearchService
import kotlinx.coroutines.launch

/** Map-first wide Desktop workspace for required constraints, preferences, and ranked results. */
@Composable
internal fun DesktopAnalysisWorkspace(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    definitions: List<MetricDefinition>,
    conditions: List<SearchCondition>,
    onConditionsChanged: (List<SearchCondition>) -> Unit,
    effectivePreferences: List<EffectiveMetricPreference>,
    preferenceOverrides: List<MetricPreferenceOverride>,
    onPreferenceEnabledChanged: (String, Boolean) -> Unit,
    onPreferenceWeightChanged: (String, Int) -> Unit,
    onPreferenceThresholdsChanged: (String, Double, Double) -> Unit,
    onPreferenceReset: (String) -> Unit,
    center: Settlement?,
    onCenterChanged: (Settlement?) -> Unit,
    radiusText: String,
    activeRadiusKm: Double?,
    onRadiusChanged: (String) -> Unit,
    radiusError: String?,
    summary: String,
    rankedResults: List<ScoredSettlement>,
    rankedCandidatesBySettlementId: Map<String, RankedSettlementResult>,
    favorites: List<FavoriteSettlement>,
    favoriteSettlementsById: Map<String, Settlement>,
    onFavoriteAdded: (ScoredSettlement) -> Unit,
    onFavoriteRemoved: (String) -> Unit,
    onFavoritesCleared: () -> Unit,
    onFavoriteNoteChanged: (String, String?) -> Unit,
    onFavoriteSnapshotUpdated: (String) -> Unit,
    candidateScope: SettlementCandidateScope,
    importedCandidateList: ImportedCandidateList?,
    onImportedCandidatesApplied: (String, List<String>) -> Unit,
    onImportedCandidatesActivated: () -> Unit,
    onImportedCandidatesDeactivated: () -> Unit,
    onImportedCandidatesCleared: () -> Unit,
    settlementImportResolver: SettlementListImportResolver,
    onImportSettlementListFile: ((String) -> String?)?,
    selected: SettlementDetails?,
    running: Boolean,
    error: String?,
    settlementSearch: SettlementSearchService,
    onSelect: (Settlement) -> Unit,
    onCloseSelected: () -> Unit,
    onImportDataset: () -> Unit,
    externalLinks: ExternalLinkOpener,
    searchProviders: List<ExternalSearchProvider>,
    onExternalSearchSettingsSave: (List<ExternalSearchProviderTermsUpdate>) -> Unit,
    favoriteNotebookExporter: FavoriteNotebookExporter?,
    settlementDisplayName: (Settlement) -> String = { it.name },
    mapContent: @Composable (Modifier, (String) -> Unit, ((GeoPoint) -> Unit)?) -> Unit,
) {
    var filterPickerOpen by remember { mutableStateOf(false) }
    var candidateImportOpen by remember { mutableStateOf(false) }
    var favoritesOpen by remember { mutableStateOf(false) }
    var resultsFocusRequest by remember { mutableStateOf(0) }
    var mapCenterPickerActive by remember { mutableStateOf(false) }
    var mapCenterPickerResolving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val definitionMap = remember(definitions) { definitions.associateBy { it.id } }
    val selectedScore =
        selected?.settlement?.id?.let { selectedId ->
            rankedCandidatesBySettlementId[selectedId]?.result?.score
        }
    var settlementPaneMode by remember { mutableStateOf(DesktopSettlementPaneMode.SUMMARY) }
    LaunchedEffect(selected?.settlement?.id) {
        settlementPaneMode = DesktopSettlementPaneMode.SUMMARY
    }

    val onMapSettlementActivated: (String) -> Unit = mapSettlement@{ settlementId ->
        if (mapCenterPickerActive) return@mapSettlement
        val settlement = rankedResults.firstOrNull { it.settlement.id == settlementId }?.settlement
            ?: return@mapSettlement
        onSelect(settlement)
    }
    val onMapLocationActivated: ((GeoPoint) -> Unit)? =
        if (!mapCenterPickerActive || mapCenterPickerResolving) {
            null
        } else {
            { location ->
                mapCenterPickerResolving = true
                scope.launch {
                    try {
                        settlementSearch.nearestTo(location)?.let { nearest ->
                            mapCenterPickerActive = false
                            onCenterChanged(nearest)
                        }
                    } finally {
                        mapCenterPickerResolving = false
                    }
                }
            }
        }

    BoxWithConstraints(modifier.fillMaxSize()) {
        var panelWidthDp by remember(maxWidth) {
            mutableStateOf(
                DesktopWorkspaceLayoutPolicy.initialSidebarWidthDp(maxWidth.value),
            )
        }
        val dragState =
            rememberDraggableState { deltaPx ->
                val deltaDp = with(density) { deltaPx.toDp().value }
                panelWidthDp =
                    DesktopWorkspaceLayoutPolicy.resizedSidebarWidthDp(panelWidthDp, deltaDp)
            }

        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(panelWidthDp.dp).fillMaxHeight()) {
                DesktopAnalysisSidebar(
                    modifier = Modifier.fillMaxSize(),
                    datasetInfo = datasetInfo,
                    definitions = definitions,
                    conditions = conditions,
                    onConditionsChanged = onConditionsChanged,
                    effectivePreferences = effectivePreferences,
                    preferenceOverrides = preferenceOverrides,
                    onPreferenceEnabledChanged = onPreferenceEnabledChanged,
                    onPreferenceWeightChanged = onPreferenceWeightChanged,
                    onPreferenceThresholdsChanged = onPreferenceThresholdsChanged,
                    onPreferenceReset = onPreferenceReset,
                    center = center,
                    onCenterChanged = { nextCenter ->
                        mapCenterPickerActive = false
                        onCenterChanged(nextCenter)
                    },
                    mapCenterPickerActive = mapCenterPickerActive,
                    onMapCenterPickerToggle = { mapCenterPickerActive = !mapCenterPickerActive },
                    mapCenterPickerEnabled = datasetInfo != null && !mapCenterPickerResolving,
                    radiusText = radiusText,
                    onRadiusChanged = onRadiusChanged,
                    radiusError = radiusError,
                    summary = summary,
                    candidateScope = candidateScope,
                    importedCandidateList = importedCandidateList,
                    onCandidateImportRequested = {
                        mapCenterPickerActive = false
                        filterPickerOpen = false
                        candidateImportOpen = true
                    },
                    onActivateImported = onImportedCandidatesActivated,
                    onDeactivateImported = onImportedCandidatesDeactivated,
                    onClearImported = onImportedCandidatesCleared,
                    rankedResults = rankedResults,
                    favorites = favorites,
                    onFavoriteAdded = onFavoriteAdded,
                    onFavoriteRemoved = onFavoriteRemoved,
                    onFavoritesRequested = {
                        filterPickerOpen = false
                        candidateImportOpen = false
                        favoritesOpen = true
                    },
                    resultsFocusRequest = resultsFocusRequest,
                    selectedId = selected?.settlement?.id,
                    running = running,
                    error = error,
                    settlementSearch = settlementSearch,
                    onSelect = onSelect,
                    onImportDataset = onImportDataset,
                    onAddFilterRequested = {
                        mapCenterPickerActive = false
                        candidateImportOpen = false
                        filterPickerOpen = true
                    },
                    settlementDisplayName = settlementDisplayName,
                )

                if (filterPickerOpen) {
                    DesktopFilterPicker(
                        modifier = Modifier.fillMaxSize(),
                        definitions = definitions,
                        conditions = conditions,
                        onAdd = { definition ->
                            onConditionsChanged(
                                conditions + SearchCondition(metricId = definition.id),
                            )
                            filterPickerOpen = false
                        },
                        onClose = { filterPickerOpen = false },
                    )
                }

                if (candidateImportOpen) {
                    CandidateImportPanel(
                        modifier = Modifier.fillMaxSize(),
                        resolver = settlementImportResolver,
                        initialText = importedCandidateList?.sourceText.orEmpty(),
                        center = center,
                        radiusKm = activeRadiusKm,
                        onLoadTextFile = onImportSettlementListFile,
                        onApply = { sourceText, settlementIds ->
                            onImportedCandidatesApplied(sourceText, settlementIds)
                            candidateImportOpen = false
                        },
                        onClose = { candidateImportOpen = false },
                        settlementDisplayName = settlementDisplayName,
                    )
                }

                if (favoritesOpen) {
                    FavoritesPanel(
                        modifier = Modifier.fillMaxSize(),
                        favorites = favorites,
                        settlementsById = favoriteSettlementsById,
                        rankedCandidatesBySettlementId = rankedCandidatesBySettlementId,
                        hasEnabledPreferences = effectivePreferences.any { it.enabled },
                        activeSettlementId = selected?.settlement?.id,
                        onSelect = onSelect,
                        onOpen = { settlement ->
                            onSelect(settlement)
                            favoritesOpen = false
                        },
                        onRemove = onFavoriteRemoved,
                        onClear = onFavoritesCleared,
                        onNoteChanged = onFavoriteNoteChanged,
                        onUpdateSnapshot = onFavoriteSnapshotUpdated,
                        onCopySelectedToImport = { sourceText, settlementIds ->
                            onImportedCandidatesApplied(sourceText, settlementIds)
                            favoritesOpen = false
                            resultsFocusRequest += 1
                        },
                        searchProviders = searchProviders,
                        propertySearchTerms = datasetInfo?.propertySearchTerms ?: "property",
                        onExternalSearchUrlOpen = externalLinks::open,
                        onExternalSearchSettingsSave = onExternalSearchSettingsSave,
                        favoriteNotebookExporter = favoriteNotebookExporter,
                        onClose = { favoritesOpen = false },
                        settlementDisplayName = settlementDisplayName,
                    )
                }
            }

            Box(
                Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = dragState,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val dividerHeight = 1.dp
                // Keep the Intel macOS JCEF host rectangle stable while navigating results.
                // A large dynamic SwingPanel resize can leave the native browser window painting
                // into the newly allocated Compose lower pane. Reserving the same lower area in
                // both selected and unselected states avoids that platform-specific resize path.
                val lowerPaneHeight =
                    maxHeight / (
                        DesktopWorkspaceLayoutPolicy.MAP_HEIGHT_WEIGHT +
                            DesktopWorkspaceLayoutPolicy.DETAILS_HEIGHT_WEIGHT
                    )
                val mapHeight = (maxHeight - lowerPaneHeight - dividerHeight).coerceAtLeast(0.dp)

                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(mapHeight)) {
                        mapContent(Modifier.fillMaxSize(), onMapSettlementActivated, onMapLocationActivated)
                    }

                    HorizontalDivider(Modifier.height(dividerHeight))

                    if (selected == null) {
                        ResultsStatusPanel(
                            modifier = Modifier.fillMaxWidth().height(lowerPaneHeight),
                            resultCount = rankedResults.size,
                            running = running,
                        )
                    } else {
                        when (settlementPaneMode) {
                            DesktopSettlementPaneMode.SUMMARY ->
                                SettlementSummaryPanel(
                                    modifier = Modifier.fillMaxWidth().height(lowerPaneHeight),
                                    details = selected,
                                    score = selectedScore,
                                    definitions = definitionMap,
                                    conditions = conditions,
                                    effectivePreferences = effectivePreferences,
                                    resultCount = rankedResults.size,
                                    running = running,
                                    hasExternalSearch = searchProviders.isNotEmpty(),
                                    onDetails = { settlementPaneMode = DesktopSettlementPaneMode.DETAILS },
                                    onExternalSearch = { settlementPaneMode = DesktopSettlementPaneMode.EXTERNAL_SEARCH },
                                    onClose = onCloseSelected,
                                    settlementDisplayName = settlementDisplayName,
                                )
                            DesktopSettlementPaneMode.DETAILS ->
                                SettlementDetailedInfoPanel(
                                    modifier = Modifier.fillMaxWidth().height(lowerPaneHeight),
                                    details = selected,
                                    score = selectedScore,
                                    hasEnabledPreferences = effectivePreferences.any { it.enabled },
                                    definitions = definitionMap,
                                    onBack = { settlementPaneMode = DesktopSettlementPaneMode.SUMMARY },
                                    settlementDisplayName = settlementDisplayName,
                                )
                            DesktopSettlementPaneMode.EXTERNAL_SEARCH ->
                                SettlementExternalSearchPanel(
                                    modifier = Modifier.fillMaxWidth().height(lowerPaneHeight),
                                    details = selected,
                                    datasetInfo = datasetInfo,
                                    externalLinks = externalLinks,
                                    searchProviders = searchProviders,
                                    onExternalSearchSettingsSave = onExternalSearchSettingsSave,
                                    onBack = { settlementPaneMode = DesktopSettlementPaneMode.SUMMARY },
                                    settlementDisplayName = settlementDisplayName,
                                )
                        }
                    }
                }
            }
        }
    }
}
