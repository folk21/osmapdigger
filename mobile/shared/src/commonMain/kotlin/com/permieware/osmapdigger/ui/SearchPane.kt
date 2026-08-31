package com.permieware.osmapdigger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.presentation.MetricFilterPresentationBuilder
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.external.ExternalSearchUrlBuilder
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.external.ExternalLinkOpener
import com.permieware.osmapdigger.search.SettlementSearchService
import kotlinx.coroutines.launch

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
                            settlement.placeType,
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

@Composable
internal fun CenterSelector(
    settlementSearch: SettlementSearchService,
    center: Settlement?,
    onCenterChanged: (Settlement?) -> Unit,
    mapPickerActive: Boolean = false,
    onMapPickerToggle: (() -> Unit)? = null,
    mapPickerEnabled: Boolean = false,
    radiusText: String,
    onRadiusChanged: (String) -> Unit,
    radiusError: String?,
    settlementDisplayName: (Settlement) -> String = { it.name },
) {
    val strings = LocalUiStrings.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val centerDisplayName = center?.let(settlementDisplayName)
    var suggestions by remember { mutableStateOf<List<SettlementSearchMatch>>(emptyList()) }

    LaunchedEffect(center?.id, centerDisplayName) {
        if (centerDisplayName != null) {
            query = centerDisplayName
        }
    }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.searchAreaOptional, style = MaterialTheme.typography.titleSmall)

            if (center != null) {
                AssistChip(
                    onClick = {
                        query = ""
                        onCenterChanged(null)
                    },
                    label = { Text(strings.center(settlementDisplayName(center))) },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    query = value
                    suggestions = emptyList()
                    if (center != null && value != centerDisplayName) {
                        onCenterChanged(null)
                    }
                },
                label = { Text(strings.centerSettlementOptional) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val found =
                                if (query.isBlank()) emptyList() else settlementSearch.find(query)
                            val exactMatches = found.filter { it.kind == SettlementMatchKind.EXACT }
                            if (exactMatches.size == 1) {
                                val exact = exactMatches.single().settlement
                                onCenterChanged(exact)
                                query = settlementDisplayName(exact)
                                suggestions = emptyList()
                            } else {
                                suggestions = found
                            }
                        }
                    },
                    enabled = query.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.findCenter)
                }

                if (onMapPickerToggle != null) {
                    OutlinedButton(
                        onClick = onMapPickerToggle,
                        enabled = mapPickerEnabled || mapPickerActive,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (mapPickerActive) strings.cancelCenterPick else strings.pickCenterOnMap)
                    }
                }
            }

            if (mapPickerActive) {
                Text(
                    strings.mapPickHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            suggestions.take(6).forEach { match ->
                TextButton(
                    onClick = {
                        onCenterChanged(match.settlement)
                        suggestions = emptyList()
                        query = settlementDisplayName(match.settlement)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        val displayName = settlementDisplayName(match.settlement)
                        Text(strings.useAsCenter(displayName))
                        val details =
                            listOfNotNull(
                                match.matchedName.takeIf { it != displayName },
                                match.settlement.placeType,
                                match.kind.name.lowercase().takeIf { match.kind == SettlementMatchKind.FUZZY },
                            ).joinToString(" · ")
                        if (details.isNotBlank()) {
                            Text(details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = onRadiusChanged,
                    label = { Text(strings.radiusOptional) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = center != null,
                    isError = radiusError != null,
                    supportingText = {
                        val message = radiusError ?: when {
                            center == null -> strings.selectCenterFirst
                            radiusText.trim().toDoubleOrNull() == 0.0 -> strings.zeroRadiusHint
                            else -> null
                        }
                        if (message != null) {
                            Text(message)
                        }
                    },
                )
                if (radiusText.isNotBlank()) {
                    TextButton(
                        onClick = { onRadiusChanged("") },
                        enabled = center != null,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(strings.clear)
                    }
                }
            }
        }
    }
}

/**
 * Render arbitrary min/max numeric filters from persisted MetricDefinition rows.
 * No OSM category IDs are enumerated here; additive builder metrics therefore appear
 * through the same UI contract after rebuilding a dataset.
 */
@Composable
internal fun DynamicFilters(
    definitions: List<MetricDefinition>,
    conditions: List<SearchCondition>,
    onConditionsChanged: (List<SearchCondition>) -> Unit,
    title: String? = null,
    onAddFilterRequested: (() -> Unit)? = null,
) {
    val strings = LocalUiStrings.current
    val resolvedTitle = title ?: strings.filters
    val definitionMap = remember(definitions) { definitions.associateBy { it.id } }
    var menuExpanded by remember { mutableStateOf(false) }
    val activeIds = conditions.mapTo(mutableSetOf()) { it.metricId }
    val available = definitions.filterNot { it.id in activeIds }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(resolvedTitle, style = MaterialTheme.typography.titleSmall)
                Box {
                    TextButton(
                        onClick = {
                            if (onAddFilterRequested != null) {
                                onAddFilterRequested()
                            } else {
                                menuExpanded = true
                            }
                        },
                    ) {
                        Text(strings.addFilter)
                    }
                    if (onAddFilterRequested == null) {
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            available.forEach { definition ->
                                DropdownMenuItem(
                                    text = {
                                        val presentation = MetricFilterPresentationBuilder.build(definition, strings.metricFilterText())
                                        Column {
                                            Text(presentation.title)
                                            Text(
                                                presentation.chooserDescription,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onConditionsChanged(conditions + SearchCondition(metricId = definition.id))
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            conditions.forEach { condition ->
                val definition = definitionMap[condition.metricId] ?: return@forEach
                MetricRangeRow(
                    definition = definition,
                    condition = condition,
                    onChange = { updated ->
                        onConditionsChanged(
                            conditions.map { if (it.metricId == condition.metricId) updated else it },
                        )
                    },
                    onRemove = {
                        onConditionsChanged(conditions.filterNot { it.metricId == condition.metricId })
                    },
                )
            }
        }
    }
}

/** Render one optional lower/upper range without assigning semantics to empty fields. */
@Composable
private fun MetricRangeRow(
    definition: MetricDefinition,
    condition: SearchCondition,
    onChange: (SearchCondition) -> Unit,
    onRemove: () -> Unit,
) {
    val strings = LocalUiStrings.current
    var minText by remember(condition.metricId, condition.minValue) {
        mutableStateOf(condition.minValue?.toString() ?: "")
    }
    var maxText by remember(condition.metricId, condition.maxValue) {
        mutableStateOf(condition.maxValue?.toString() ?: "")
    }

    val presentation = remember(definition, strings.language) { MetricFilterPresentationBuilder.build(definition, strings.metricFilterText()) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(presentation.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${definition.group} · ${presentation.chooserDescription}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(onClick = onRemove) { Text(strings.remove) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minText,
                onValueChange = {
                    minText = it
                    onChange(condition.copy(minValue = it.toDoubleOrNull()))
                },
                label = { Text(presentation.minLabel) },
                suffix = { presentation.fieldUnit?.let { Text(it) } },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = {
                    maxText = it
                    onChange(condition.copy(maxValue = it.toDoubleOrNull()))
                },
                label = { Text(presentation.maxLabel) },
                suffix = { presentation.fieldUnit?.let { Text(it) } },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun SettlementDetailsCard(
    details: SettlementDetails,
    datasetInfo: DatasetInfo?,
    externalLinks: ExternalLinkOpener,
    searchProviders: List<ExternalSearchProvider>,
    onExternalSearchSettingsSave: (List<ExternalSearchProviderTermsUpdate>) -> Unit,
    settlementDisplayName: (Settlement) -> String = { it.name },
) {
    val strings = LocalUiStrings.current
    var externalSearchSettingsOpen by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.settlementDetails, style = MaterialTheme.typography.labelLarge)
            Text(settlementDisplayName(details.settlement), style = MaterialTheme.typography.titleMedium)
            Text(
                "${details.settlement.location.latitude}, ${details.settlement.location.longitude}",
                style = MaterialTheme.typography.bodySmall,
            )

            details.metrics
                .groupBy { it.definition.group }
                .forEach { (group, values) ->
                    Text(group, style = MaterialTheme.typography.labelLarge)
                    values.take(16).forEach { metric ->
                        Text(
                            "${metric.definition.title}: ${formatMetric(metric.value, metric.definition.unit)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

            if (searchProviders.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.externalPropertySearch, style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { externalSearchSettingsOpen = !externalSearchSettingsOpen }) {
                        Text(strings.externalSearchSettings)
                    }
                }
                if (externalSearchSettingsOpen) {
                    ExternalSearchTermsSettings(
                        providers = searchProviders,
                        datasetTerms = datasetInfo?.propertySearchTerms ?: "property",
                        onSave = { updates ->
                            onExternalSearchSettingsSave(updates)
                            externalSearchSettingsOpen = false
                        },
                        onClose = { externalSearchSettingsOpen = false },
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    }
}

internal fun formatMetric(value: Double, unit: String): String {
    val formatted = NumberFormatter.compact(value)
    return if (unit == "count") formatted else "$formatted $unit"
}
