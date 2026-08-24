package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.PropertySearchLinks
import com.permieware.osmapdigger.runtime.ExternalLinkOpener
import com.permieware.osmapdigger.runtime.GeoRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Shared search/details surface driven entirely by runtime metric definitions.
 *
 * The pane owns presentation state only. It delegates name lookup/details to the
 * repository and delegates actual structured search execution to its parent callback.
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
    results: List<Settlement>,
    selected: SettlementDetails?,
    summary: String,
    running: Boolean,
    error: String?,
    repository: GeoRepository,
    onSearch: () -> Unit,
    onSelect: (Settlement) -> Unit,
    onImportDataset: () -> Unit,
    externalLinks: ExternalLinkOpener,
) {
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
                    Text(datasetInfo?.displayName ?: "Loading dataset…")
                }
                TextButton(onClick = onImportDataset) { Text("Change") }
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
                repository = repository,
                center = center,
                onCenterChanged = onCenterChanged,
                radiusText = radiusText,
                onRadiusChanged = onRadiusChanged,
            )
        }

        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Generated search query", style = MaterialTheme.typography.titleSmall)
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Button(
                onClick = onSearch,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "Searching…" else "Search settlements")
            }
        }

        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

        item { Text("Found settlements (${results.size})", style = MaterialTheme.typography.titleMedium) }

        items(results, key = { it.id }) { settlement ->
            OutlinedCard(
                onClick = { onSelect(settlement) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(settlement.name, style = MaterialTheme.typography.titleSmall)
                    val subtitle =
                        listOfNotNull(
                            settlement.placeType,
                            settlement.population?.let { "population $it" },
                        ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        selected?.let { details ->
            item { SettlementDetailsCard(details = details, datasetInfo = datasetInfo, externalLinks = externalLinks) }
        }
    }
}

@Composable
private fun CenterSelector(
    repository: GeoRepository,
    center: Settlement?,
    onCenterChanged: (Settlement?) -> Unit,
    radiusText: String,
    onRadiusChanged: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Settlement>>(emptyList()) }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Search area (optional)", style = MaterialTheme.typography.titleSmall)

            if (center != null) {
                AssistChip(
                    onClick = { onCenterChanged(null) },
                    label = { Text("Center: ${center.name} ×") },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Center settlement (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = {
                    scope.launch {
                        suggestions = if (query.isBlank()) emptyList() else repository.findSettlements(query)
                    }
                },
                enabled = query.isNotBlank(),
            ) {
                Text("Find center")
            }

            suggestions.take(6).forEach { settlement ->
                TextButton(
                    onClick = {
                        onCenterChanged(settlement)
                        suggestions = emptyList()
                        query = settlement.name
                    },
                ) {
                    Text(settlement.name)
                }
            }

            OutlinedTextField(
                value = radiusText,
                onValueChange = onRadiusChanged,
                label = { Text("Radius from center, km (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

/**
 * Render arbitrary min/max numeric filters from persisted MetricDefinition rows.
 * No OSM category IDs are enumerated here; additive builder metrics therefore appear
 * through the same UI contract after rebuilding a dataset.
 */
@Composable
private fun DynamicFilters(
    definitions: List<MetricDefinition>,
    conditions: List<SearchCondition>,
    onConditionsChanged: (List<SearchCondition>) -> Unit,
) {
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
                Text("Filters", style = MaterialTheme.typography.titleSmall)
                Box {
                    TextButton(onClick = { menuExpanded = true }) { Text("Add filter") }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        available.forEach { definition ->
                            DropdownMenuItem(
                                text = { Text("${definition.group} · ${definition.title}") },
                                onClick = {
                                    onConditionsChanged(conditions + SearchCondition(metricId = definition.id))
                                    menuExpanded = false
                                },
                            )
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
    var minText by remember(condition.metricId, condition.minValue) {
        mutableStateOf(condition.minValue?.toString() ?: "")
    }
    var maxText by remember(condition.metricId, condition.maxValue) {
        mutableStateOf(condition.maxValue?.toString() ?: "")
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(definition.title, style = MaterialTheme.typography.bodyMedium)
                Text(definition.group, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minText,
                onValueChange = {
                    minText = it
                    onChange(condition.copy(minValue = it.toDoubleOrNull()))
                },
                label = { Text("From") },
                suffix = { Text(definition.unit) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = {
                    maxText = it
                    onChange(condition.copy(maxValue = it.toDoubleOrNull()))
                },
                label = { Text("To") },
                suffix = { Text(definition.unit) },
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
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settlement details", style = MaterialTheme.typography.labelLarge)
            Text(details.settlement.name, style = MaterialTheme.typography.titleMedium)
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

            Text("External property search", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExternalSearchProvider.entries.forEach { provider ->
                    OutlinedButton(
                        onClick = {
                            externalLinks.open(
                                PropertySearchLinks.build(
                                    provider = provider,
                                    settlement = details.settlement,
                                    site = datasetInfo?.propertySearchSite,
                                    terms = datasetInfo?.propertySearchTerms ?: "property",
                                ),
                            )
                        },
                    ) {
                        Text(provider.title)
                    }
                }
            }
        }
    }
}

private fun formatMetric(value: Double, unit: String): String {
    val rounded = if (value % 1.0 == 0.0) value.toInt().toString() else ((value * 100).roundToInt() / 100.0).toString()
    return if (unit == "count") rounded else "$rounded $unit"
}
