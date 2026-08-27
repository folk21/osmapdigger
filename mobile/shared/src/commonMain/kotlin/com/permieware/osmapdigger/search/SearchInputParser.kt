package com.permieware.osmapdigger.search

/** Parsing rules for textual search inputs before they become domain request values. */
object SearchInputParser {
    fun positiveRadiusKm(value: String): Double? =
        value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
}
