package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.domain.MetricDefinition

/** Human-facing filter semantics derived only from persisted metric metadata. */
data class MetricFilterPresentation(
    val title: String,
    val chooserDescription: String,
    val minLabel: String,
    val maxLabel: String,
    val fieldUnit: String?,
)

/**
 * Maps generic persisted metric types to clear filter controls without enumerating metric IDs.
 * Unknown future measure types retain the persisted title/unit and generic range labels.
 */
object MetricFilterPresentationBuilder {
    fun build(definition: MetricDefinition): MetricFilterPresentation =
        when (definition.measureType.lowercase()) {
            "distance" ->
                MetricFilterPresentation(
                    title = definition.title,
                    chooserDescription = "Distance to the nearest mapped feature${unitSuffix(definition.unit)}",
                    minLabel = "Min distance",
                    maxLabel = "Max distance",
                    fieldUnit = definition.unit.takeIf { it.isNotBlank() },
                )

            "count" ->
                MetricFilterPresentation(
                    title = definition.title,
                    chooserDescription = "Number of mapped features inside the fixed radius in this metric",
                    minLabel = "Min number",
                    maxLabel = "Max number",
                    fieldUnit = null,
                )

            "coverage" ->
                MetricFilterPresentation(
                    title = definition.title,
                    chooserDescription = "Area share covered by mapped features${unitSuffix(definition.unit)}",
                    minLabel = "Min coverage",
                    maxLabel = "Max coverage",
                    fieldUnit = definition.unit.takeIf { it.isNotBlank() },
                )

            else ->
                MetricFilterPresentation(
                    title = definition.title,
                    chooserDescription = definition.description.ifBlank {
                        listOf(definition.measureType, definition.unit)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    },
                    minLabel = "Min value",
                    maxLabel = "Max value",
                    fieldUnit = definition.unit.takeIf { it.isNotBlank() && it != "count" },
                )
        }

    private fun unitSuffix(unit: String): String =
        unit.takeIf { it.isNotBlank() && it != "count" }?.let { " · $it" }.orEmpty()
}
