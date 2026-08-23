package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.domain.*
import kotlin.test.Test
import kotlin.test.assertTrue

class FilterSummaryBuilderTest {
    @Test
    fun buildsReadableSummary() {
        val definition =
            MetricDefinition(
                id = "water.distance_km",
                categoryId = "water",
                group = "Nature",
                title = "Distance to water",
                description = "",
                unit = "km",
                measureType = "distance",
                preferredDirection = PreferredDirection.LOWER,
                defaultEnabled = true,
                sortOrder = 0,
            )
        val request = SearchRequest(conditions = listOf(SearchCondition(definition.id, maxValue = 5.0)))

        val text = FilterSummaryBuilder.build(request, mapOf(definition.id to definition))
        assertTrue(text.contains("Distance to water"))
        assertTrue(text.contains("5 km"))
    }
}
