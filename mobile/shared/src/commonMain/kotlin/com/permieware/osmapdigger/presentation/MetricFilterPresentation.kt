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

/** Localized generic labels for metric-type filter presentation. */
data class MetricFilterPresentationText(
    val minDistance: String,
    val maxDistance: String,
    val minNumber: String,
    val maxNumber: String,
    val minCoverage: String,
    val maxCoverage: String,
    val minValue: String,
    val maxValue: String,
    val nearestMappedFeature: (String) -> String,
    val mappedFeaturesInFixedRadius: String,
    val mappedAreaCoverage: (String) -> String,
)

/**
 * Maps generic persisted metric types to clear filter controls without enumerating metric IDs.
 * Unknown future measure types retain the persisted title/unit and generic range labels.
 */
object MetricFilterPresentationBuilder {
    fun build(
        definition: MetricDefinition,
        text: MetricFilterPresentationText = EnglishText,
    ): MetricFilterPresentation =
        when (definition.measureType.lowercase()) {
            "distance" ->
                MetricFilterPresentation(
                    title = definition.title,
                    chooserDescription = text.nearestMappedFeature(unitSuffix(definition.unit)),
                    minLabel = text.minDistance,
                    maxLabel = text.maxDistance,
                    fieldUnit = definition.unit.takeIf { it.isNotBlank() },
                )

            "count" ->
                MetricFilterPresentation(
                    title = definition.title,
                    chooserDescription = text.mappedFeaturesInFixedRadius,
                    minLabel = text.minNumber,
                    maxLabel = text.maxNumber,
                    fieldUnit = null,
                )

            "coverage" ->
                MetricFilterPresentation(
                    title = definition.title,
                    chooserDescription = text.mappedAreaCoverage(unitSuffix(definition.unit)),
                    minLabel = text.minCoverage,
                    maxLabel = text.maxCoverage,
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
                    minLabel = text.minValue,
                    maxLabel = text.maxValue,
                    fieldUnit = definition.unit.takeIf { it.isNotBlank() && it != "count" },
                )
        }

    private fun unitSuffix(unit: String): String =
        unit.takeIf { it.isNotBlank() && it != "count" }?.let { " · $it" }.orEmpty()

    private val EnglishText =
        MetricFilterPresentationText(
            minDistance = "Min distance",
            maxDistance = "Max distance",
            minNumber = "Min number",
            maxNumber = "Max number",
            minCoverage = "Min coverage",
            maxCoverage = "Max coverage",
            minValue = "Min value",
            maxValue = "Max value",
            nearestMappedFeature = { suffix -> "Distance to the nearest mapped feature$suffix" },
            mappedFeaturesInFixedRadius = "Number of mapped features inside the fixed radius in this metric",
            mappedAreaCoverage = { suffix -> "Area share covered by mapped features$suffix" },
        )
}
