package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.analysis.MetricPreference
import com.permieware.osmapdigger.analysis.PreferenceScorer
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.PreferredDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreExplanationBuilderTest {
    @Test
    fun selectsComparativeAdvantageAndCompromiseAgainstEligibleCandidateBaseline() {
        val preferences =
            listOf(
                MetricPreference("forest", PreferredDirection.LOWER, 0.0, 10.0, 8),
                MetricPreference("beach", PreferredDirection.LOWER, 0.0, 10.0, 6),
                MetricPreference("medical", PreferredDirection.LOWER, 0.0, 10.0, 4),
                MetricPreference("school", PreferredDirection.LOWER, 0.0, 10.0, 2),
            )
        val selected =
            PreferenceScorer.score(
                preferences,
                mapOf("forest" to 0.0, "beach" to 0.0, "medical" to 7.5),
            )
        val peers =
            listOf(
                PreferenceScorer.score(
                    preferences,
                    mapOf("forest" to 0.0, "beach" to 8.0, "medical" to 2.0),
                ),
                PreferenceScorer.score(
                    preferences,
                    mapOf("forest" to 0.5, "beach" to 7.0, "medical" to 3.0),
                ),
            )
        val baseline = ScoreExplanationBuilder.baseline(listOf(selected) + peers)

        val explanation =
            ScoreExplanationBuilder.build(
                selected,
                definitions =
                    mapOf(
                        "forest" to definition("forest", "Distance to forest", "km"),
                        "beach" to definition("beach", "Distance to beach", "km"),
                        "medical" to definition("medical", "Distance to medical facility", "km"),
                        "school" to definition("school", "Distance to school", "km"),
                    ),
                baseline = baseline,
            )

        assertEquals("beach", explanation.advantage?.contribution?.metricId)
        assertEquals("medical", explanation.compromise?.contribution?.metricId)
        assertEquals(listOf("school"), explanation.unknown.map { it.contribution.metricId })
        assertEquals("Distance to beach", explanation.advantage?.title)
        assertTrue((explanation.advantage?.comparativeImpact ?: 0.0) > 0.0)
        assertTrue((explanation.compromise?.comparativeImpact ?: 0.0) < 0.0)
        assertTrue((baseline.averageQualityByMetricId.getValue("forest")) > 0.98)
    }

    @Test
    fun metricThatMatchesCandidateAverageProducesNoComparativeCue() {
        val preference = MetricPreference("forest", PreferredDirection.LOWER, 0.0, 10.0, 5)
        val score = PreferenceScorer.score(listOf(preference), mapOf("forest" to 2.0))
        val baseline = ScoreExplanationBuilder.baseline(listOf(score))

        val explanation = ScoreExplanationBuilder.build(score, emptyMap(), baseline)

        assertNull(explanation.advantage)
        assertNull(explanation.compromise)
    }

    @Test
    fun baselineIgnoresUnknownPreferenceValues() {
        val preferences =
            listOf(
                MetricPreference("forest", PreferredDirection.LOWER, 0.0, 10.0, 5),
                MetricPreference("medical", PreferredDirection.LOWER, 0.0, 10.0, 5),
            )
        val first = PreferenceScorer.score(preferences, mapOf("forest" to 2.0))
        val second = PreferenceScorer.score(preferences, mapOf("forest" to 6.0, "medical" to 4.0))

        val baseline = ScoreExplanationBuilder.baseline(listOf(first, second))

        assertEquals(0.6, baseline.averageQualityByMetricId.getValue("forest"), 1e-9)
        assertEquals(0.6, baseline.averageQualityByMetricId.getValue("medical"), 1e-9)
    }

    private fun definition(id: String, title: String, unit: String) =
        MetricDefinition(
            id = id,
            categoryId = id,
            group = "Test",
            title = title,
            description = title,
            unit = unit,
            measureType = "distance",
            preferredDirection = PreferredDirection.LOWER,
            defaultEnabled = false,
            sortOrder = 0,
        )
}
