package com.permieware.osmapdigger.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MapScaleCalculatorTest {
    @Test
    fun scaleUsesConventionalDistanceAndFitsTargetWidth() {
        val scale = assertNotNull(MapScaleCalculator.calculate(latitude = 53.9, zoom = 10.0, targetWidthPx = 100.0))

        assertTrue(scale.distanceMeters in setOf(1000.0, 2000.0, 5000.0, 10000.0, 20000.0))
        assertTrue(scale.widthFractionOfTarget in 0.1..1.0)
    }

    @Test
    fun invalidCameraInputDoesNotProduceScale() {
        assertEquals(null, MapScaleCalculator.calculate(Double.NaN, 10.0, 100.0))
        assertEquals(null, MapScaleCalculator.calculate(53.9, Double.NaN, 100.0))
        assertEquals(null, MapScaleCalculator.calculate(53.9, 10.0, 0.0))
    }
}
