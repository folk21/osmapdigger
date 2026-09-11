package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.analysis.PreferenceContribution
import com.permieware.osmapdigger.analysis.SettlementScore
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.PreferredDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferenceDataAvailabilityBuilderTest {
    @Test
    fun reportsMissingPreferencesInStableMetricOrder() {
        val availability =
            PreferenceDataAvailabilityBuilder.build(
                score(
                    contribution("z.metric", rawValue = null),
                    contribution("a.metric", rawValue = 1.0),
                    contribution("m.metric", rawValue = null),
                ),
            )

        assertEquals(3, availability.totalPreferenceCount)
        assertEquals(1, availability.knownPreferenceCount)
        assertEquals(listOf("m.metric", "z.metric"), availability.missingMetricIds)
        assertFalse(availability.isScoreUnavailableBecauseAllPreferenceDataIsMissing)
    }

    @Test
    fun identifiesUnavailableScoreWhenEveryEnabledPreferenceIsMissing() {
        val availability =
            PreferenceDataAvailabilityBuilder.build(
                score(
                    contribution("a.metric", rawValue = null),
                    contribution("b.metric", rawValue = null),
                ),
            )

        assertEquals(2, availability.missingPreferenceCount)
        assertTrue(availability.isScoreUnavailableBecauseAllPreferenceDataIsMissing)
    }

    @Test
    fun resolvesMissingCriteriaWithLocalizedMetricSemanticsAndStableFallback() {
        val availability =
            PreferenceDataAvailability(
                totalPreferenceCount = 2,
                knownPreferenceCount = 0,
                missingMetricIds = listOf("industrial.distance_km", "future.metric"),
            )
        val definition =
            MetricDefinition(
                id = "industrial.distance_km",
                categoryId = "industrial",
                group = "Risks",
                title = "Industrial area",
                description = "",
                unit = "km",
                measureType = "distance",
                preferredDirection = PreferredDirection.HIGHER,
                defaultEnabled = true,
                sortOrder = 1,
            )

        assertEquals(
            listOf("Промышленная зона — расстояние", "future.metric"),
            PreferenceDataAvailabilityPresenter.missingTitles(
                availability,
                mapOf(definition.id to definition),
                UiLanguage.RUSSIAN,
            ),
        )
    }

    private fun score(vararg contributions: PreferenceContribution) =
        SettlementScore(
            value = null,
            coverage = 0.0,
            knownWeight = 0,
            totalWeight = contributions.sumOf { it.weight },
            contributions = contributions.toList(),
        )

    private fun contribution(metricId: String, rawValue: Double?) =
        PreferenceContribution(
            metricId = metricId,
            direction = PreferredDirection.LOWER,
            targetValue = 1.0,
            limitValue = 10.0,
            weight = 1,
            rawValue = rawValue,
            quality = rawValue?.let { 1.0 },
            weightedContribution = rawValue?.let { 1.0 },
            scoreContribution = rawValue?.let { 100.0 },
        )
}
