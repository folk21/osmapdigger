package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.SearchRequest
import com.permieware.osmapdigger.presentation.NumberFormatter

/** Produces a human-readable equivalent of the visual filter state without generative AI. */
object FilterSummaryBuilder {
    fun build(
        request: SearchRequest,
        definitions: Map<String, MetricDefinition>,
    ): String {
        val parts = mutableListOf<String>()

        request.center?.let { center ->
            if (request.radiusKm != null) {
                parts += "within ${NumberFormatter.compact(request.radiusKm)} km of ${center.name}"
            } else {
                parts += "around ${center.name}"
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
                        "${definition.title}: at least ${NumberFormatter.compact(condition.minValue)}$unit"
                    condition.maxValue != null ->
                        "${definition.title}: up to ${NumberFormatter.compact(condition.maxValue)}$unit"
                    else -> return@forEach
                }
            }

        return if (parts.isEmpty()) {
            "No filters: show settlements from the current dataset."
        } else {
            "Find settlements " + parts.joinToString(", ") + "."
        }
    }

}
