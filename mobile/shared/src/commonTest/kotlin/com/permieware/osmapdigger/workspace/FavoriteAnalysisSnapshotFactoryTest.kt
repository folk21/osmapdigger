package com.permieware.osmapdigger.workspace

import com.permieware.osmapdigger.analysis.MetricPreference
import com.permieware.osmapdigger.analysis.PreferenceContribution
import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.analysis.SettlementScore
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteAnalysisSnapshotFactoryTest {
    @Test
    fun capturesOnlyEffectiveRequiredAndEnabledPreferencesFromAuthoritativeScore() {
        val settlement = Settlement("s", "Saved", null, null, "village", null, GeoPoint(1.0, 2.0))
        val center = Settlement("c", "Center", null, null, "town", null, GeoPoint(0.0, 0.0))
        val contribution = PreferenceContribution(
            metricId = "forest.distance_km",
            direction = PreferredDirection.LOWER,
            targetValue = 1.0,
            limitValue = 10.0,
            weight = 8,
            rawValue = 2.5,
            quality = 0.8,
            weightedContribution = 6.4,
            scoreContribution = 80.0,
        )
        val result = ScoredSettlement(
            settlement,
            SettlementScore(80.0, 100.0, 8, 8, listOf(contribution)),
        )
        val definitions = listOf(
            metric("forest.distance_km", "Forest"),
            metric("landfill.distance_km", "Landfill"),
        )
        val enabled = EffectiveMetricPreference(
            "forest.distance_km",
            true,
            MetricPreference("forest.distance_km", PreferredDirection.LOWER, 1.0, 10.0, 8),
        )
        val disabled = EffectiveMetricPreference(
            "landfill.distance_km",
            false,
            MetricPreference("landfill.distance_km", PreferredDirection.HIGHER, 10.0, 1.0, 5),
        )

        val draft = FavoriteAnalysisSnapshotFactory.create(
            datasetId = "dataset",
            result = result,
            definitions = definitions,
            conditions = listOf(
                SearchCondition("landfill.distance_km", minValue = 5.0),
                SearchCondition("forest.distance_km"),
            ),
            effectivePreferences = listOf(enabled, disabled),
            center = center,
            radiusKm = 25.0,
        )

        assertEquals("dataset", draft.datasetId)
        assertEquals("s", draft.settlementId)
        assertEquals(listOf("landfill.distance_km"), draft.requiredCriteria.map { it.metricId })
        assertEquals(listOf("forest.distance_km"), draft.preferences.map { it.metricId })
        assertEquals(2.5, draft.preferences.single().rawValue)
        assertEquals("c", draft.centerSettlementId)
        assertEquals(25.0, draft.radiusKm)
    }

    private fun metric(id: String, title: String) = MetricDefinition(
        id = id,
        categoryId = id.substringBefore('.'),
        group = "Test",
        title = title,
        description = title,
        unit = "km",
        measureType = "distance",
        preferredDirection = PreferredDirection.LOWER,
        defaultEnabled = false,
        sortOrder = 0,
    )
}
