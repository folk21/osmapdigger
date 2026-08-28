package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.analysis.MetricPreference
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricValue
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettlementCriteriaSummaryBuilderTest {
    @Test
    fun prioritizesEffectiveRequiredMetricsThenEnabledPreferencesWithoutDuplicates() {
        val forest = definition("forest", "Forest", "km")
        val water = definition("water", "Water", "km")
        val school = definition("school", "School", "km")
        val medical = definition("medical", "Medical", "km")
        val details =
            SettlementDetails(
                settlement = settlement(),
                metrics = listOf(MetricValue(forest, 1.2), MetricValue(water, 2.4), MetricValue(school, 3.6)),
            )

        val result =
            SettlementCriteriaSummaryBuilder.build(
                details = details,
                definitions = listOf(forest, water, school, medical).associateBy { it.id },
                conditions =
                    listOf(
                        SearchCondition("forest", maxValue = 5.0),
                        SearchCondition("water"),
                        SearchCondition("school", minValue = 0.5),
                    ),
                preferences =
                    listOf(
                        preference("forest", enabled = true),
                        preference("medical", enabled = false),
                        preference("water", enabled = true),
                    ),
            )

        assertEquals(listOf("forest", "school", "water"), result.map { it.metricId })
        assertEquals(listOf(1.2, 3.6, 2.4), result.map { it.value })
    }

    @Test
    fun preservesUnknownValueForParticipatingMetricMissingFromDetails() {
        val medical = definition("medical", "Medical", "km")
        val result =
            SettlementCriteriaSummaryBuilder.build(
                details = SettlementDetails(settlement(), emptyList()),
                definitions = mapOf(medical.id to medical),
                conditions = emptyList(),
                preferences = listOf(preference("medical", enabled = true)),
            )

        assertEquals(1, result.size)
        assertEquals("Medical", result.single().title)
        assertNull(result.single().value)
    }

    @Test
    fun limitsCompactSummaryToThreeMetricsByDefault() {
        val definitions = (1..4).map { definition("m$it", "Metric $it", "km") }
        val result =
            SettlementCriteriaSummaryBuilder.build(
                details = SettlementDetails(settlement(), definitions.mapIndexed { index, it -> MetricValue(it, index.toDouble()) }),
                definitions = definitions.associateBy { it.id },
                conditions = definitions.map { SearchCondition(it.id, maxValue = 10.0) },
                preferences = emptyList(),
            )

        assertEquals(listOf("m1", "m2", "m3"), result.map { it.metricId })
    }

    private fun preference(metricId: String, enabled: Boolean) =
        EffectiveMetricPreference(
            metricId = metricId,
            enabled = enabled,
            preference = MetricPreference(metricId, PreferredDirection.LOWER, 1.0, 10.0, 5),
        )

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

    private fun settlement() =
        Settlement(
            id = "s1",
            name = "Settlement",
            localName = null,
            englishName = null,
            placeType = "town",
            population = 1_000,
            location = GeoPoint(42.0, 1.0),
        )
}
