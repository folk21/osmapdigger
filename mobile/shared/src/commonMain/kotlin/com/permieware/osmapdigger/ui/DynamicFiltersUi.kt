package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.presentation.MetricFilterPresentationBuilder

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
