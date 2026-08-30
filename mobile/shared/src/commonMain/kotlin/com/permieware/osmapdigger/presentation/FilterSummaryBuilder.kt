package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.SearchRequest

/** Language-specific connective text for deterministic filter summaries. */
data class FilterSummaryText(
    val noFilters: String,
    val findSettlements: String,
    val withinCenter: (String, String) -> String,
    val aroundCenter: (String) -> String,
    val atLeast: (String) -> String,
    val upTo: (String) -> String,
)

/** Produces a human-readable equivalent of the visual filter state without generative AI. */
object FilterSummaryBuilder {
    fun build(
        request: SearchRequest,
        definitions: Map<String, MetricDefinition>,
        text: FilterSummaryText = EnglishFilterSummaryText,
    ): String {
        val parts = mutableListOf<String>()

        request.center?.let { center ->
            if (request.radiusKm != null) {
                parts += text.withinCenter(NumberFormatter.compact(request.radiusKm), center.name)
            } else {
                parts += text.aroundCenter(center.name)
            }
        }

        request.conditions
            .filter { it.isEffective }
            .forEach { condition ->
                val definition = definitions[condition.metricId] ?: return@forEach
                val unit = if (definition.unit == "count") "" else " ${definition.unit}"
                parts += when {
                    condition.minValue != null && condition.maxValue != null ->
                        "${definition.title}: ${NumberFormatter.compact(condition.minValue)}–${NumberFormatter.compact(condition.maxValue)}$unit"
                    condition.minValue != null ->
                        "${definition.title}: ${text.atLeast(NumberFormatter.compact(condition.minValue))}$unit"
                    condition.maxValue != null ->
                        "${definition.title}: ${text.upTo(NumberFormatter.compact(condition.maxValue))}$unit"
                    else -> return@forEach
                }
            }

        return if (parts.isEmpty()) {
            text.noFilters
        } else {
            text.findSettlements + " " + parts.joinToString(", ") + "."
        }
    }

    private val EnglishFilterSummaryText =
        FilterSummaryText(
            noFilters = "No filters: show settlements from the current dataset.",
            findSettlements = "Find settlements",
            withinCenter = { radius, center -> "within $radius km of $center" },
            aroundCenter = { center -> "around $center" },
            atLeast = { value -> "at least $value" },
            upTo = { value -> "up to $value" },
        )
}
