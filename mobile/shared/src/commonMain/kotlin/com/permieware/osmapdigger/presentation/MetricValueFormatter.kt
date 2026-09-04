package com.permieware.osmapdigger.presentation

/** Formats persisted numeric metric values without assigning semantics to metric IDs. */
object MetricValueFormatter {
    fun known(value: Double, unit: String): String {
        val formatted = NumberFormatter.compact(value)
        return if (unit == "count") formatted else "$formatted $unit"
    }

    fun optional(value: Double?, unit: String, unknownValue: String): String {
        val formatted = value?.let(NumberFormatter::compact) ?: unknownValue
        return if (value == null || unit.isBlank() || unit == "count") formatted else "$formatted $unit"
    }
}
