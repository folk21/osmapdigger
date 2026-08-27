package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.Settlement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreferenceScorerTest {
    @Test
    fun lowerDirectionSaturatesAndInterpolatesLinearly() {
        val preference = preference("forest", PreferredDirection.LOWER, target = 2.0, limit = 10.0)

        assertEquals(1.0, score(preference, 1.0).contributions.single().quality)
        assertEquals(1.0, score(preference, 2.0).contributions.single().quality)
        assertEquals(0.5, assertNotNull(score(preference, 6.0).contributions.single().quality), TOLERANCE)
        assertEquals(0.0, score(preference, 10.0).contributions.single().quality)
        assertEquals(0.0, score(preference, 12.0).contributions.single().quality)
    }

    @Test
    fun higherDirectionSaturatesAndInterpolatesLinearly() {
        val preference = preference("farm", PreferredDirection.HIGHER, target = 10.0, limit = 2.0)

        assertEquals(0.0, score(preference, 1.0).contributions.single().quality)
        assertEquals(0.0, score(preference, 2.0).contributions.single().quality)
        assertEquals(0.5, assertNotNull(score(preference, 6.0).contributions.single().quality), TOLERANCE)
        assertEquals(1.0, score(preference, 10.0).contributions.single().quality)
        assertEquals(1.0, score(preference, 12.0).contributions.single().quality)
    }

    @Test
    fun weightedScoreUsesOnlyKnownPreferenceWeights() {
        val forest = preference("forest", PreferredDirection.LOWER, 2.0, 10.0, weight = 3)
        val water = preference("water", PreferredDirection.LOWER, 2.0, 10.0, weight = 1)

        val score =
            PreferenceScorer.score(
                preferences = listOf(forest, water),
                metricValues = mapOf("forest" to 2.0, "water" to 6.0),
            )

        assertEquals(87.5, assertNotNull(score.value), TOLERANCE)
        assertEquals(100.0, score.coverage, TOLERANCE)
        assertEquals(4, score.knownWeight)
        assertEquals(4, score.totalWeight)
        assertEquals(75.0, assertNotNull(score.contributions[0].scoreContribution), TOLERANCE)
        assertEquals(12.5, assertNotNull(score.contributions[1].scoreContribution), TOLERANCE)
    }

    @Test
    fun missingMetricIsUnknownAndReducesCoverageWithoutReducingScore() {
        val forest = preference("forest", PreferredDirection.LOWER, 2.0, 10.0, weight = 3)
        val water = preference("water", PreferredDirection.LOWER, 2.0, 10.0, weight = 1)

        val score =
            PreferenceScorer.score(
                preferences = listOf(forest, water),
                metricValues = mapOf("forest" to 2.0),
            )

        assertEquals(100.0, assertNotNull(score.value), TOLERANCE)
        assertEquals(75.0, score.coverage, TOLERANCE)
        assertEquals(3, score.knownWeight)
        assertEquals(4, score.totalWeight)
        assertEquals(true, score.contributions[0].isKnown)
        assertEquals(false, score.contributions[1].isKnown)
        assertNull(score.contributions[1].rawValue)
        assertNull(score.contributions[1].quality)
        assertNull(score.contributions[1].weightedContribution)
        assertNull(score.contributions[1].scoreContribution)
    }

    @Test
    fun unavailableScoreIsNullWhenNoEnabledPreferenceHasData() {
        val preference = preference("forest", PreferredDirection.LOWER, 2.0, 10.0)

        val score = PreferenceScorer.score(listOf(preference), emptyMap())

        assertNull(score.value)
        assertEquals(0.0, score.coverage, TOLERANCE)
        assertEquals(0, score.knownWeight)
        assertEquals(5, score.totalWeight)
    }

    @Test
    fun emptyPreferenceSetHasUnavailableScoreAndZeroCoverage() {
        val score = PreferenceScorer.score(emptyList(), emptyMap())

        assertNull(score.value)
        assertEquals(0.0, score.coverage, TOLERANCE)
        assertEquals(0, score.knownWeight)
        assertEquals(0, score.totalWeight)
        assertEquals(emptyList(), score.contributions)
    }

    @Test
    fun invalidPreferenceContractsFailFast() {
        assertFailsWith<IllegalArgumentException> {
            preference("neutral", PreferredDirection.NEUTRAL, 1.0, 2.0)
        }
        assertFailsWith<IllegalArgumentException> {
            preference("lower", PreferredDirection.LOWER, 10.0, 2.0)
        }
        assertFailsWith<IllegalArgumentException> {
            preference("higher", PreferredDirection.HIGHER, 2.0, 10.0)
        }
        assertFailsWith<IllegalArgumentException> {
            preference("weight", PreferredDirection.LOWER, 2.0, 10.0, weight = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            preference("nan", PreferredDirection.LOWER, Double.NaN, 10.0)
        }
    }

    @Test
    fun duplicateMetricPreferencesAndNonFiniteRuntimeValuesFailFast() {
        val preference = preference("forest", PreferredDirection.LOWER, 2.0, 10.0)

        assertFailsWith<IllegalArgumentException> {
            PreferenceScorer.score(listOf(preference, preference), mapOf("forest" to 2.0))
        }
        assertFailsWith<IllegalArgumentException> {
            PreferenceScorer.score(listOf(preference), mapOf("forest" to Double.POSITIVE_INFINITY))
        }
    }

    private fun score(
        preference: MetricPreference,
        value: Double,
    ): SettlementScore = PreferenceScorer.score(listOf(preference), mapOf(preference.metricId to value))

    private fun preference(
        id: String,
        direction: PreferredDirection,
        target: Double,
        limit: Double,
        weight: Int = 5,
    ) = MetricPreference(id, direction, target, limit, weight)

    companion object {
        private const val TOLERANCE = 1e-9
    }
}

