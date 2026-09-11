package com.permieware.osmapdigger.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class MetricValueFormatterTest {
    @Test
    fun countValuesDoNotExposeCountAsAUnit() {
        assertEquals("3", MetricValueFormatter.known(3.0, "count"))
        assertEquals("3", MetricValueFormatter.optional(3.0, "count", "unknown"))
    }

    @Test
    fun optionalValuesPreserveUnitAndUnknownPresentation() {
        assertEquals("2.35 km", MetricValueFormatter.optional(2.345, "km", "unknown"))
        assertEquals("unknown", MetricValueFormatter.optional(null, "km", "unknown"))
    }
}
