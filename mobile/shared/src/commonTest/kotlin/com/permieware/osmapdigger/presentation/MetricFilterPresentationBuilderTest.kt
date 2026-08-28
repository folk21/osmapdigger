package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.PreferredDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricFilterPresentationBuilderTest {
    @Test
    fun distanceUsesDistanceLabelsAndKilometres() {
        val presentation = MetricFilterPresentationBuilder.build(definition("distance", "km"))

        assertEquals("Min distance", presentation.minLabel)
        assertEquals("Max distance", presentation.maxLabel)
        assertEquals("km", presentation.fieldUnit)
        assertTrue(presentation.chooserDescription.contains("nearest"))
    }

    @Test
    fun countUsesNumberLabelsWithoutCountSuffix() {
        val presentation = MetricFilterPresentationBuilder.build(definition("count", "count"))

        assertEquals("Min number", presentation.minLabel)
        assertEquals("Max number", presentation.maxLabel)
        assertEquals(null, presentation.fieldUnit)
        assertTrue(presentation.chooserDescription.contains("Number of mapped features"))
        assertFalse(presentation.chooserDescription.endsWith("count"))
    }

    @Test
    fun coverageUsesCoverageLabelsAndPercent() {
        val presentation = MetricFilterPresentationBuilder.build(definition("coverage", "%"))

        assertEquals("Min coverage", presentation.minLabel)
        assertEquals("Max coverage", presentation.maxLabel)
        assertEquals("%", presentation.fieldUnit)
        assertTrue(presentation.chooserDescription.contains("Area share"))
    }

    @Test
    fun unknownMeasureTypeFallsBackToGenericRange() {
        val presentation = MetricFilterPresentationBuilder.build(definition("density", "items/km²"))

        assertEquals("Min value", presentation.minLabel)
        assertEquals("Max value", presentation.maxLabel)
        assertEquals("items/km²", presentation.fieldUnit)
    }

    private fun definition(measureType: String, unit: String) =
        MetricDefinition(
            id = "railway_station.example",
            categoryId = "railway_station",
            group = "Infrastructure",
            title = "Railway station example",
            description = "",
            unit = unit,
            measureType = measureType,
            preferredDirection = PreferredDirection.NEUTRAL,
            defaultEnabled = false,
            sortOrder = 0,
        )
}
