package com.permieware.osmapdigger.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MetricPreferenceDefaultTest {
    @Test
    fun acceptsValidLowerAndHigherDefaults() {
        val lower =
            MetricPreferenceDefault(
                metricId = "forest.distance_km",
                direction = PreferredDirection.LOWER,
                targetValue = 1.0,
                limitValue = 10.0,
                weight = 8,
                defaultEnabled = true,
            )
        val higher =
            MetricPreferenceDefault(
                metricId = "landfill.distance_km",
                direction = PreferredDirection.HIGHER,
                targetValue = 15.0,
                limitValue = 3.0,
                weight = 9,
                defaultEnabled = false,
            )

        assertEquals(8, lower.weight)
        assertEquals(PreferredDirection.HIGHER, higher.direction)
    }

    @Test
    fun rejectsNeutralDirectionAndInvalidBounds() {
        assertFailsWith<IllegalArgumentException> {
            MetricPreferenceDefault(
                metricId = "neutral.metric",
                direction = PreferredDirection.NEUTRAL,
                targetValue = 1.0,
                limitValue = 2.0,
                weight = 5,
                defaultEnabled = false,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MetricPreferenceDefault(
                metricId = "forest.distance_km",
                direction = PreferredDirection.LOWER,
                targetValue = 10.0,
                limitValue = 1.0,
                weight = 5,
                defaultEnabled = true,
            )
        }
    }

    @Test
    fun rejectsOutOfRangeWeightAndNonFiniteValues() {
        assertFailsWith<IllegalArgumentException> {
            MetricPreferenceDefault(
                metricId = "forest.distance_km",
                direction = PreferredDirection.LOWER,
                targetValue = 1.0,
                limitValue = 10.0,
                weight = 0,
                defaultEnabled = true,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MetricPreferenceDefault(
                metricId = "forest.distance_km",
                direction = PreferredDirection.LOWER,
                targetValue = Double.NaN,
                limitValue = 10.0,
                weight = 5,
                defaultEnabled = true,
            )
        }
    }
}
