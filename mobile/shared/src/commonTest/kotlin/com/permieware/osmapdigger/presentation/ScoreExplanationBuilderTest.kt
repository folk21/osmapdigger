package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.analysis.PreferenceScorer
import com.permieware.osmapdigger.analysis.MetricPreference
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.PreferredDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoreExplanationBuilderTest {
    @Test
    fun selectsStrongestWeakestAndUnknownContributionsDeterministically() {
        val preferences =
            listOf(
                MetricPreference("forest", PreferredDirection.LOWER, 2.0, 10.0, 8),
                MetricPreference("water", PreferredDirection.LOWER, 2.0, 10.0, 4),
                MetricPreference("medical", PreferredDirection.LOWER, 5.0, 25.0, 2),
            )
        val score =
            PreferenceScorer.score(
                preferences,
                mapOf(
                    "forest" to 2.0,
                    "water" to 10.0,
                ),
            )

        val explanation =
            ScoreExplanationBuilder.build(
                score,
                definitions =
                    mapOf(
                        "forest" to definition("forest", "Distance to forest", "km"),
                        "water" to definition("water", "Distance to water", "km"),
                        "medical" to definition("medical", "Distance to medical facility", "km"),
                    ),
            )

        assertEquals("forest", explanation.strongest?.contribution?.metricId)
        assertEquals("water", explanation.weakest?.contribution?.metricId)
        assertEquals(listOf("medical"), explanation.unknown.map { it.contribution.metricId })
        assertEquals("Distance to forest", explanation.strongest?.title)
    }

    @Test
    fun singleKnownContributionIsNotRepeatedAsWeakest() {
        val preference = MetricPreference("forest", PreferredDirection.LOWER, 2.0, 10.0, 5)
        val score = PreferenceScorer.score(listOf(preference), mapOf("forest" to 2.0))

        val explanation = ScoreExplanationBuilder.build(score, emptyMap())

        assertEquals("forest", explanation.strongest?.title)
        assertNull(explanation.weakest)
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
