package com.permieware.osmapdigger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.external.ExternalLinkOpener
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.presentation.SettlementPlaceTypeResolver
import com.permieware.osmapdigger.search.SettlementSearchService

/**
 * Shared search/details surface driven entirely by runtime metric definitions.
 *
 * The pane owns presentation state only. It delegates name lookup/details to the
 * shared settlement-search service and delegates structured search execution to its parent callback.
 */
@Composable
internal fun SearchPane(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    definitions: List<MetricDefinition>,
    conditions: List<SearchCondition>,
    onConditionsChanged: (List<SearchCondition>) -> Unit,
    center: Settlement?,
    onCenterChanged: (Settlement?) -> Unit,
    radiusText: String,
    onRadiusChanged: (String) -> Unit,
    radiusError: String?,
    results: List<Settlement>,
    selected: SettlementDetails?,
    summary: String,
    running: Boolean,
    error: String?,
    settlementSearch: SettlementSearchService,
    onSearch: () -> Unit,
    onSelect: (Settlement) -> Unit,
    onImportDataset: () -> Unit,
    externalLinks: ExternalLinkOpener,
    searchProviders: List<ExternalSearchProvider>,
    onExternalSearchSettingsSave: (List<ExternalSearchProviderTermsUpdate>) -> Unit,
    settlementDisplayName: (Settlement) -> String = { it.name },
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("OsmapDigger", style = MaterialTheme.typography.headlineSmall)
                    Text(datasetInfo?.displayName ?: strings.loadingDataset)
                }
                Row { LanguageSelector(); TextButton(onClick = onImportDataset) { Text(strings.changeDataset) } }
            }
        }

        item {
            DynamicFilters(
                definitions = definitions,
                conditions = conditions,
                onConditionsChanged = onConditionsChanged,
            )
        }

        item {
            CenterSelector(
                settlementSearch = settlementSearch,
                center = center,
                onCenterChanged = onCenterChanged,
                radiusText = radiusText,
                onRadiusChanged = onRadiusChanged,
                radiusError = radiusError,
                settlementDisplayName = settlementDisplayName,
            )
        }

        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.generatedSearchQuery, style = MaterialTheme.typography.titleSmall)
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Button(
                onClick = onSearch,
                enabled = !running && radiusError == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) strings.searching else strings.searchSettlements)
            }
        }

        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

        item { Text(strings.foundSettlements(results.size), style = MaterialTheme.typography.titleMedium) }

        items(results, key = { it.id }) { settlement ->
            val isSelected = selected?.settlement?.id == settlement.id
            OutlinedCard(
                onClick = { onSelect(settlement) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                ),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(settlementDisplayName(settlement), style = MaterialTheme.typography.titleSmall)
                    val subtitle =
                        listOfNotNull(
                            SettlementPlaceTypeResolver.resolve(settlement.placeType, language),
                            settlement.population?.let(strings.population),
                        ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        selected?.let { details ->
            item {
                SettlementDetailsCard(
                    details = details,
                    datasetInfo = datasetInfo,
                    externalLinks = externalLinks,
                    searchProviders = searchProviders,
                    onExternalSearchSettingsSave = onExternalSearchSettingsSave,
                    settlementDisplayName = settlementDisplayName,
                )
            }
        }
    }
}
