package com.permieware.osmapdigger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.analysis.ImportedCandidateList
import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.analysis.RankedSettlementResult
import com.permieware.osmapdigger.analysis.SettlementCandidateScope
import com.permieware.osmapdigger.analysis.SettlementScore
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.external.ExternalSearchUrlBuilder
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference
import com.permieware.osmapdigger.preferences.MetricPreferenceOverride
import com.permieware.osmapdigger.presentation.UiStrings
import com.permieware.osmapdigger.presentation.DesktopWorkspaceLayoutPolicy
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.presentation.ScoreExplanationBuilder
import com.permieware.osmapdigger.presentation.SettlementPlaceTypeResolver
import com.permieware.osmapdigger.presentation.SettlementCriteriaSummaryBuilder
import com.permieware.osmapdigger.presentation.TwoRowLayout
import com.permieware.osmapdigger.external.ExternalLinkOpener
import com.permieware.osmapdigger.search.SettlementSearchService
import com.permieware.osmapdigger.search.SettlementListImportResolver
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.permieware.osmapdigger.presentation.MetricDisplayNameResolver
import com.permieware.osmapdigger.presentation.MetricFilterPresentationBuilder


// Header, center, candidate source, Required, summary, Preferences, and ranked-results heading precede result rows.
private const val DESKTOP_SIDEBAR_STATIC_ITEMS_BEFORE_RESULTS = 7

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

@Composable
private fun DesktopAnalysisSidebar(
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
    mapCenterPickerActive: Boolean,
    onMapCenterPickerToggle: () -> Unit,
    mapCenterPickerEnabled: Boolean,
    radiusText: String,
    onRadiusChanged: (String) -> Unit,
    radiusError: String?,
    summary: String,
    candidateScope: SettlementCandidateScope,
    importedCandidateList: ImportedCandidateList?,
    onCandidateImportRequested: () -> Unit,
    onActivateImported: () -> Unit,
    onDeactivateImported: () -> Unit,
    onClearImported: () -> Unit,
    rankedResults: List<ScoredSettlement>,
    favorites: List<FavoriteSettlement>,
    onFavoriteAdded: (ScoredSettlement) -> Unit,
    onFavoriteRemoved: (String) -> Unit,
    onFavoritesRequested: () -> Unit,
    resultsFocusRequest: Int,
    selectedId: String?,
    running: Boolean,
    error: String?,
    settlementSearch: SettlementSearchService,
    onSelect: (Settlement) -> Unit,
    onImportDataset: () -> Unit,
    onAddFilterRequested: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    val definitionMap = remember(definitions) { definitions.associateBy { it.id } }
    val favoriteIds = remember(favorites) { favorites.mapTo(hashSetOf()) { it.settlementId } }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId) {
        val resultIndex = rankedResults.indexOfFirst { it.settlement.id == selectedId }
        if (resultIndex >= 0) {
            val firstResultItemIndex =
                DESKTOP_SIDEBAR_STATIC_ITEMS_BEFORE_RESULTS +
                    (if (running) 1 else 0) +
                    (if (error != null) 1 else 0)
            listState.animateScrollToItem(firstResultItemIndex + resultIndex)
        }
    }

    LaunchedEffect(resultsFocusRequest) {
        if (resultsFocusRequest > 0) {
            val resultsHeaderIndex =
                DESKTOP_SIDEBAR_STATIC_ITEMS_BEFORE_RESULTS - 1 +
                    (if (running) 1 else 0) +
                    (if (error != null) 1 else 0)
            listState.animateScrollToItem(resultsHeaderIndex.coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("OsmapDigger", style = MaterialTheme.typography.headlineSmall)
                    Text(datasetInfo?.displayName ?: strings.loadingDataset)
                }
                Row { LanguageSelector(); TextButton(onClick = onImportDataset) { Text(strings.changeDataset) } }
            }
        }

        item {
            CenterSelector(
                settlementSearch = settlementSearch,
                center = center,
                onCenterChanged = onCenterChanged,
                mapPickerActive = mapCenterPickerActive,
                onMapPickerToggle = onMapCenterPickerToggle,
                mapPickerEnabled = mapCenterPickerEnabled,
                radiusText = radiusText,
                onRadiusChanged = onRadiusChanged,
                radiusError = radiusError,
                settlementDisplayName = settlementDisplayName,
            )
        }

        item {
            CandidateSourceSummary(
                candidateScope = candidateScope,
                importedCandidateList = importedCandidateList,
                onImportRequested = onCandidateImportRequested,
                onActivateImported = onActivateImported,
                onDeactivateImported = onDeactivateImported,
                onClearImported = onClearImported,
            )
        }

        item {
            DynamicFilters(
                definitions = definitions,
                conditions = conditions,
                onConditionsChanged = onConditionsChanged,
                title = strings.required,
                onAddFilterRequested = onAddFilterRequested,
            )
        }

        item {
            Text(summary, style = MaterialTheme.typography.bodySmall)
        }

        item {
            PreferencesSection(
                definitions = definitions,
                effectivePreferences = effectivePreferences,
                preferenceOverrides = preferenceOverrides,
                onEnabledChanged = onPreferenceEnabledChanged,
                onWeightChanged = onPreferenceWeightChanged,
                onThresholdsChanged = onPreferenceThresholdsChanged,
                onReset = onPreferenceReset,
            )
        }

        if (running) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

        item {
            RankedResultsHeader(
                rankedResultCount = rankedResults.size,
                favoriteCount = favorites.size,
                onOpenFavorites = onFavoritesRequested,
            )
        }

        itemsIndexed(rankedResults, key = { _, item -> item.settlement.id }) { index, result ->
            RankedSettlementCard(
                rank = index + 1,
                result = result,
                definitions = definitionMap,
                hasEnabledPreferences = effectivePreferences.any { it.enabled },
                selected = result.settlement.id == selectedId,
                favorite = result.settlement.id in favoriteIds,
                onClick = { onSelect(result.settlement) },
                onFavoriteChanged = { included ->
                    if (included) onFavoriteAdded(result) else onFavoriteRemoved(result.settlement.id)
                },
                displayName = settlementDisplayName(result.settlement),
            )
        }
    }
}

@Composable
private fun DesktopFilterPicker(
    modifier: Modifier,
    definitions: List<MetricDefinition>,
    conditions: List<SearchCondition>,
    onAdd: (MetricDefinition) -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val activeIds = remember(conditions) { conditions.mapTo(hashSetOf()) { it.metricId } }
    val available = remember(definitions, activeIds) {
        definitions.filterNot { it.id in activeIds }
    }

    Surface(
        modifier = modifier,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(strings.addRequiredFilter, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        strings.chooseRequiredMetric,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onClose) { Text(strings.close) }
            }

            HorizontalDivider()

            if (available.isEmpty()) {
                Text(strings.allRequiredMetricsUsed)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(available, key = { it.id }) { definition ->
                        OutlinedCard(
                            onClick = { onAdd(definition) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val presentation = MetricFilterPresentationBuilder.build(definition, strings.metricFilterText())
                            Column(Modifier.padding(12.dp)) {
                                Text(presentation.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    presentation.chooserDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    definition.group,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesSection(
    definitions: List<MetricDefinition>,
    effectivePreferences: List<EffectiveMetricPreference>,
    preferenceOverrides: List<MetricPreferenceOverride>,
    onEnabledChanged: (String, Boolean) -> Unit,
    onWeightChanged: (String, Int) -> Unit,
    onThresholdsChanged: (String, Double, Double) -> Unit,
    onReset: (String) -> Unit,
) {
    val strings = LocalUiStrings.current
    val definitionMap = remember(definitions) { definitions.associateBy { it.id } }
    val overriddenIds = remember(preferenceOverrides) { preferenceOverrides.mapTo(hashSetOf()) { it.metricId } }
    var expandedMetricId by remember { mutableStateOf<String?>(null) }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.preferences, style = MaterialTheme.typography.titleSmall)
            if (effectivePreferences.isEmpty()) {
                Text(
                    strings.noPreferenceDefaults,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            effectivePreferences.forEach { effective ->
                val definition = definitionMap[effective.metricId]
                PreferenceRow(
                    title = definition?.title ?: effective.metricId,
                    unit = definition?.unit.orEmpty(),
                    effective = effective,
                    overridden = effective.metricId in overriddenIds,
                    expanded = expandedMetricId == effective.metricId,
                    onExpandedChange = { expanded ->
                        expandedMetricId = if (expanded) effective.metricId else null
                    },
                    onEnabledChanged = { onEnabledChanged(effective.metricId, it) },
                    onWeightChanged = { onWeightChanged(effective.metricId, it) },
                    onThresholdsChanged = { target, limit ->
                        onThresholdsChanged(effective.metricId, target, limit)
                    },
                    onReset = { onReset(effective.metricId) },
                )
            }
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    unit: String,
    effective: EffectiveMetricPreference,
    overridden: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onWeightChanged: (Int) -> Unit,
    onThresholdsChanged: (Double, Double) -> Unit,
    onReset: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val preference = effective.preference
    var targetText by remember(effective.metricId, preference.targetValue) {
        mutableStateOf(preference.targetValue.toString())
    }
    var limitText by remember(effective.metricId, preference.limitValue) {
        mutableStateOf(preference.limitValue.toString())
    }
    val target = targetText.toDoubleOrNull()
    val limit = limitText.toDoubleOrNull()
    val thresholdsValid =
        target != null && limit != null && target.isFinite() && limit.isFinite() &&
            when (preference.direction) {
                PreferredDirection.LOWER -> target < limit
                PreferredDirection.HIGHER -> target > limit
                PreferredDirection.NEUTRAL -> false
            }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = effective.enabled,
                onCheckedChange = onEnabledChanged,
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onExpandedChange(!expanded) }
                    .padding(vertical = 6.dp),
            ) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    preferenceTargetSummary(preference.direction, preference.targetValue, preference.limitValue, unit, strings),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                "W ${preference.weight}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        if (expanded) {
            Column(
                Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { value ->
                            targetText = value
                            val parsedTarget = value.toDoubleOrNull()
                            val parsedLimit = limitText.toDoubleOrNull()
                            if (validThresholds(preference.direction, parsedTarget, parsedLimit)) {
                                onThresholdsChanged(parsedTarget!!, parsedLimit!!)
                            }
                        },
                        label = { Text(targetLabel(preference.direction, strings)) },
                        suffix = { if (unit.isNotBlank()) Text(unit) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = !thresholdsValid,
                    )
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { value ->
                            limitText = value
                            val parsedTarget = targetText.toDoubleOrNull()
                            val parsedLimit = value.toDoubleOrNull()
                            if (validThresholds(preference.direction, parsedTarget, parsedLimit)) {
                                onThresholdsChanged(parsedTarget!!, parsedLimit!!)
                            }
                        },
                        label = { Text(limitLabel(preference.direction, strings)) },
                        suffix = { if (unit.isNotBlank()) Text(unit) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = !thresholdsValid,
                    )
                }
                if (!thresholdsValid) {
                    Text(
                        thresholdError(preference.direction, strings),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Text(strings.weight(preference.weight), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = preference.weight.toFloat(),
                    onValueChange = { onWeightChanged(it.roundToInt().coerceIn(1, 10)) },
                    valueRange = 1f..10f,
                    steps = 8,
                )

                if (overridden) {
                    TextButton(onClick = onReset) { Text(strings.resetDatasetDefault) }
                }
            }
        }
    }
}

@Composable
private fun ResultsStatusPanel(
    modifier: Modifier,
    resultCount: Int,
    running: Boolean,
) {
    val strings = LocalUiStrings.current
    Surface(modifier) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(strings.searchResults, style = MaterialTheme.typography.labelLarge)
                Text(
                    strings.settlementsFound(resultCount),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (running) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(strings.updating.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private enum class DesktopSettlementPaneMode {
    SUMMARY,
    DETAILS,
    EXTERNAL_SEARCH,
}

@Composable
private fun SettlementSummaryPanel(
    modifier: Modifier,
    details: SettlementDetails,
    score: SettlementScore?,
    definitions: Map<String, MetricDefinition>,
    conditions: List<SearchCondition>,
    effectivePreferences: List<EffectiveMetricPreference>,
    resultCount: Int,
    running: Boolean,
    hasExternalSearch: Boolean,
    onDetails: () -> Unit,
    onExternalSearch: () -> Unit,
    onClose: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    Card(modifier) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(settlementDisplayName(details.settlement), style = MaterialTheme.typography.titleLarge)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        strings.settlementsFound(resultCount),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (running) {
                        Text(strings.updating.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            val context =
                listOfNotNull(
                    SettlementPlaceTypeResolver.resolve(details.settlement.placeType, language),
                    details.settlement.population?.let(strings.population),
                ).joinToString(" · ")
            val coordinates =
                strings.coordinates(
                    NumberFormatter.compact(details.settlement.location.latitude),
                    NumberFormatter.compact(details.settlement.location.longitude),
                )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOf(context, coordinates).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDetails) { Text(strings.details) }
                    if (hasExternalSearch) {
                        TextButton(onClick = onExternalSearch) { Text(strings.externalSearch) }
                    }
                    TextButton(onClick = onClose) { Text(strings.close) }
                }
            }

            if (effectivePreferences.any { it.enabled }) {
                val settlementScore = score
                val scoreText =
                    settlementScore?.value?.let { value ->
                        strings.scoreAndCoverage(value.roundToInt(), settlementScore.coverage.roundToInt())
                    } ?: strings.scoreUnavailable
                Text(scoreText, style = MaterialTheme.typography.titleSmall)
            }

            val criteriaSummary = remember(details, definitions, conditions, effectivePreferences, language) {
                SettlementCriteriaSummaryBuilder.build(
                    details = details,
                    definitions = definitions,
                    conditions = conditions,
                    preferences = effectivePreferences,
                ).map { item ->
                    val definition = definitions[item.metricId]
                    if (definition == null) item else item.copy(title = MetricDisplayNameResolver.resolve(definition, language))
                }
            }
            if (criteriaSummary.isNotEmpty()) {
                Text(strings.activeCriteria, style = MaterialTheme.typography.labelMedium)
                val rows = TwoRowLayout.split(criteriaSummary)
                Column(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rows.forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            rowItems.forEach { metric ->
                                Column(Modifier.width(180.dp)) {
                                    Text(metric.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    Text(
                                        formatCompactMetric(metric.value, metric.unit, strings),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementDetailedInfoPanel(
    modifier: Modifier,
    details: SettlementDetails,
    score: SettlementScore?,
    hasEnabledPreferences: Boolean,
    definitions: Map<String, MetricDefinition>,
    onBack: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    Card(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(settlementDisplayName(details.settlement), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text(strings.summary) }
            }

            val context =
                listOfNotNull(
                    SettlementPlaceTypeResolver.resolve(details.settlement.placeType, language),
                    details.settlement.population?.let(strings.population),
                ).joinToString(" · ")
            if (context.isNotBlank()) {
                Text(context, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                strings.coordinates(
                    NumberFormatter.compact(details.settlement.location.latitude),
                    NumberFormatter.compact(details.settlement.location.longitude),
                ),
                style = MaterialTheme.typography.bodySmall,
            )

            if (hasEnabledPreferences) {
                val settlementScore = score
                Text(strings.score, style = MaterialTheme.typography.labelLarge)
                val scoreText =
                    settlementScore?.value?.let { value ->
                        strings.scoreAndCoverage(value.roundToInt(), settlementScore.coverage.roundToInt())
                    } ?: strings.scoreUnavailable
                Text(scoreText, style = MaterialTheme.typography.titleMedium)
                val explanation = settlementScore?.let { currentScore ->
                    remember(currentScore, definitions) {
                        ScoreExplanationBuilder.build(currentScore, definitions)
                    }
                }
                explanation?.strongest?.let { strongest ->
                    Text(
                        "${strings.strongest}: ${contributionLabel(strongest.title, strongest.unit, strongest.contribution.rawValue, strongest.contribution.scoreContribution, strings)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                explanation?.weakest?.let { weakest ->
                    Text(
                        "${strings.weakest}: ${contributionLabel(weakest.title, weakest.unit, weakest.contribution.rawValue, weakest.contribution.scoreContribution, strings)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (explanation != null && explanation.unknown.isNotEmpty()) {
                    Text(
                        "${strings.unknown}: ${explanation.unknown.joinToString { it.title }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            details.metrics
                .groupBy { it.definition.group }
                .forEach { (group, values) ->
                    Text(group, style = MaterialTheme.typography.labelLarge)
                    values.forEach { metric ->
                        Text(
                            "${metric.definition.title}: ${formatMetric(metric.value, metric.definition.unit)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
        }
    }
}

@Composable
private fun SettlementExternalSearchPanel(
    modifier: Modifier,
    details: SettlementDetails,
    datasetInfo: DatasetInfo?,
    externalLinks: ExternalLinkOpener,
    searchProviders: List<ExternalSearchProvider>,
    onExternalSearchSettingsSave: (List<ExternalSearchProviderTermsUpdate>) -> Unit,
    onBack: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    var settingsOpen by remember { mutableStateOf(false) }
    Card(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(strings.externalSearch, style = MaterialTheme.typography.headlineSmall)
                    Text(settlementDisplayName(details.settlement), style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    if (searchProviders.isNotEmpty()) {
                        TextButton(onClick = { settingsOpen = !settingsOpen }) {
                            Text(strings.externalSearchSettings)
                        }
                    }
                    TextButton(onClick = onBack) { Text(strings.summary) }
                }
            }

            if (settingsOpen) {
                ExternalSearchTermsSettings(
                    providers = searchProviders,
                    datasetTerms = datasetInfo?.propertySearchTerms ?: "property",
                    onSave = { updates ->
                        onExternalSearchSettingsSave(updates)
                        settingsOpen = false
                    },
                    onClose = { settingsOpen = false },
                )
            } else if (searchProviders.isEmpty()) {
                Text(strings.noExternalProviders)
            } else {
                searchProviders.forEach { provider ->
                    OutlinedButton(
                        onClick = {
                            externalLinks.open(
                                ExternalSearchUrlBuilder.build(
                                    provider = provider,
                                    settlement = details.settlement,
                                    terms = datasetInfo?.propertySearchTerms ?: "property",
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(provider.title)
                    }
                }
            }
        }
    }
}

private fun formatCompactMetric(value: Double?, unit: String, strings: UiStrings): String {
    val formatted = value?.let(NumberFormatter::compact) ?: strings.unknownValue
    return if (value == null || unit.isBlank() || unit == "count") formatted else "$formatted $unit"
}

private fun contributionLabel(
    title: String,
    unit: String,
    rawValue: Double?,
    scoreContribution: Double?,
    strings: UiStrings,
): String {
    val value =
        rawValue?.let {
            val formatted = NumberFormatter.compact(it)
            if (unit.isBlank() || unit == "count") formatted else "$formatted $unit"
        } ?: strings.unknownValue
    val points = scoreContribution?.let { strings.points(NumberFormatter.compact(it)) } ?: strings.unknownContribution
    return "$title · $value · $points"
}

private fun preferenceTargetSummary(
    direction: PreferredDirection,
    target: Double,
    limit: Double,
    unit: String,
    strings: UiStrings,
): String {
    val suffix = unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    return when (direction) {
        PreferredDirection.LOWER ->
            strings.bestLowerSummary("${NumberFormatter.compact(target)}$suffix", "${NumberFormatter.compact(limit)}$suffix")
        PreferredDirection.HIGHER ->
            strings.bestHigherSummary("${NumberFormatter.compact(target)}$suffix", "${NumberFormatter.compact(limit)}$suffix")
        PreferredDirection.NEUTRAL -> strings.notScoreable
    }
}

private fun validThresholds(
    direction: PreferredDirection,
    target: Double?,
    limit: Double?,
): Boolean =
    target != null && limit != null && target.isFinite() && limit.isFinite() &&
        when (direction) {
            PreferredDirection.LOWER -> target < limit
            PreferredDirection.HIGHER -> target > limit
            PreferredDirection.NEUTRAL -> false
        }

private fun targetLabel(direction: PreferredDirection, strings: UiStrings): String =
    when (direction) {
        PreferredDirection.LOWER -> strings.bestLower
        PreferredDirection.HIGHER -> strings.bestHigher
        PreferredDirection.NEUTRAL -> strings.target
    }

private fun limitLabel(direction: PreferredDirection, strings: UiStrings): String =
    when (direction) {
        PreferredDirection.LOWER -> strings.zeroHigher
        PreferredDirection.HIGHER -> strings.zeroLower
        PreferredDirection.NEUTRAL -> strings.limit
    }

private fun thresholdError(direction: PreferredDirection, strings: UiStrings): String =
    when (direction) {
        PreferredDirection.LOWER -> strings.lowerThresholdError
        PreferredDirection.HIGHER -> strings.higherThresholdError
        PreferredDirection.NEUTRAL -> strings.neutralThresholdError
    }

