package com.permieware.osmapdigger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.analysis.ImportedCandidateList
import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.analysis.SettlementCandidateScope
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference
import com.permieware.osmapdigger.preferences.MetricPreferenceOverride
import com.permieware.osmapdigger.presentation.MetricDisplayNameResolver
import com.permieware.osmapdigger.presentation.MetricFilterPresentationBuilder
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.presentation.PreferenceQualityBaseline
import com.permieware.osmapdigger.presentation.UiStrings
import com.permieware.osmapdigger.search.SettlementSearchService

// Header, center, candidate source, Required, summary, Preferences, and ranked-results heading precede result rows.
private const val DESKTOP_SIDEBAR_STATIC_ITEMS_BEFORE_RESULTS = 7

@Composable
internal fun DesktopAnalysisSidebar(
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
    comparisonBaseline: PreferenceQualityBaseline,
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
    val language = LocalUiLanguage.current
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
                hasEnabledPreferences = effectivePreferences.any { it.enabled },
                onOpenFavorites = onFavoritesRequested,
            )
        }

        itemsIndexed(rankedResults, key = { _, item -> item.settlement.id }) { index, result ->
            RankedSettlementCard(
                rank = index + 1,
                result = result,
                definitions = definitionMap,
                comparisonBaseline = comparisonBaseline,
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
internal fun DesktopFilterPicker(
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

