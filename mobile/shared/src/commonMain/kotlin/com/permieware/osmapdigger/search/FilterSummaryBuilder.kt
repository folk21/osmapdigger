package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.SearchRequest
import kotlin.math.roundToInt

/** Produces a human-readable equivalent of the visual filter state without generative AI. */
object FilterSummaryBuilder {
    fun build(
        request: SearchRequest,
        definitions: Map<String, MetricDefinition>,
    ): String {
        val parts = mutableListOf<String>()

        request.center?.let { center ->
            if (request.radiusKm != null) {
                parts += "within ${format(request.radiusKm)} km of ${center.name}"
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
                        "${definition.title}: ${format(condition.minValue)}–${format(condition.maxValue)}$unit"
                    condition.minValue != null ->
                        "${definition.title}: at least ${format(condition.minValue)}$unit"
                    condition.maxValue != null ->
                        "${definition.title}: up to ${format(condition.maxValue)}$unit"
                    else -> return@forEach
                }
            }

        return if (parts.isEmpty()) {
            "No filters: show settlements from the current dataset."
        } else {
            "Find settlements " + parts.joinToString(", ") + "."
        }
    }

    private fun format(value: Double): String {
        if (value % 1.0 == 0.0) return value.toInt().toString()
        return ((value * 100.0).roundToInt() / 100.0).toString()
    }
}
