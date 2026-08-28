package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchUrlBuilder
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.runtime.ExternalLinkOpener
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
                settlementSearch = settlementSearch,
                center = center,
                onCenterChanged = onCenterChanged,
                radiusText = radiusText,
                onRadiusChanged = onRadiusChanged,
                radiusError = radiusError,
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
                enabled = !running && radiusError == null,
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
            item {
                SettlementDetailsCard(
                    details = details,
                    datasetInfo = datasetInfo,
                    externalLinks = externalLinks,
                    searchProviders = searchProviders,
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
    radiusText: String,
    onRadiusChanged: (String) -> Unit,
    radiusError: String?,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SettlementSearchMatch>>(emptyList()) }

    LaunchedEffect(center?.id, center?.name) {
        if (center != null) {
            query = center.name
        }
    }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Search area (optional)", style = MaterialTheme.typography.titleSmall)

            if (center != null) {
                AssistChip(
                    onClick = {
                        query = ""
                        onCenterChanged(null)
                    },
                    label = { Text("Center: ${center.name} ×") },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    query = value
                    suggestions = emptyList()
                    if (center != null && value != center.name) {
                        onCenterChanged(null)
                    }
                },
                label = { Text("Center settlement (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = {
                    scope.launch {
                        val found =
                            if (query.isBlank()) emptyList() else settlementSearch.find(query)
                        val exactMatches = found.filter { it.kind == SettlementMatchKind.EXACT }
                        if (exactMatches.size == 1) {
                            val exact = exactMatches.single().settlement
                            onCenterChanged(exact)
                            query = exact.name
                            suggestions = emptyList()
                        } else {
                            suggestions = found
                        }
                    }
                },
                enabled = query.isNotBlank(),
            ) {
                Text("Find center")
            }

            suggestions.take(6).forEach { match ->
                TextButton(
                    onClick = {
                        onCenterChanged(match.settlement)
                        suggestions = emptyList()
                        query = match.settlement.name
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Use ${match.settlement.name} as center")
                        val details =
                            listOfNotNull(
                                match.matchedName.takeIf { it != match.settlement.name },
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
                    label = { Text("Radius from center, km (optional)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = center != null,
                    isError = radiusError != null,
                    supportingText = {
                        val message = radiusError ?: when {
                            center == null -> "Select a center settlement first."
                            radiusText.trim().toDoubleOrNull() == 0.0 -> "0 means no radius limit."
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
                        Text("Clear")
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
    title: String = "Filters",
    onAddFilterRequested: (() -> Unit)? = null,
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
                Text(title, style = MaterialTheme.typography.titleSmall)
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
                        Text("Add filter")
                    }
                    if (onAddFilterRequested == null) {
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
    searchProviders: List<ExternalSearchProvider>,
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

            if (searchProviders.isNotEmpty()) {
                Text("External property search", style = MaterialTheme.typography.labelLarge)
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

internal fun formatMetric(value: Double, unit: String): String {
    val formatted = NumberFormatter.compact(value)
    return if (unit == "count") formatted else "$formatted $unit"
}
