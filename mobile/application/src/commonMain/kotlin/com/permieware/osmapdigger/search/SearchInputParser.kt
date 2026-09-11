package com.permieware.osmapdigger.search

/** Parsed state for an optional radius text field. */
sealed interface OptionalRadiusInput {
    data object Unset : OptionalRadiusInput

    data class Value(val kilometers: Double) : OptionalRadiusInput

    data object Invalid : OptionalRadiusInput
}

/** Parsing rules for textual search inputs before they become domain request values. */
object SearchInputParser {
    fun positiveRadiusKm(value: String): Double? =
        value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

    /**
     * Parses the optional radius UI contract.
     *
     * Blank input and numeric zero both mean that no radius constraint is selected. The domain
     * therefore continues to represent an unset radius as `null`; zero never enters SearchRequest.
     */
    fun optionalRadiusKm(value: String): OptionalRadiusInput {
        if (value.isBlank()) return OptionalRadiusInput.Unset
        val parsed = value.trim().toDoubleOrNull() ?: return OptionalRadiusInput.Invalid
        if (!parsed.isFinite() || parsed < 0.0) return OptionalRadiusInput.Invalid
        if (parsed == 0.0) return OptionalRadiusInput.Unset
        return OptionalRadiusInput.Value(parsed)
    }
}
