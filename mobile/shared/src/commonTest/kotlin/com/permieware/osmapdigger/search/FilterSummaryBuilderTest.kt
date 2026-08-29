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
    @Test
    fun supportsLocalizedConnectiveText() {
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
        val request =
            SearchRequest(
                center = Settlement("center", "Минск", null, null, "city", 0, GeoPoint(53.9, 27.56)),
                radiusKm = 15.0,
                conditions = listOf(SearchCondition(definition.id, maxValue = 5.0)),
            )
        val localized =
            FilterSummaryText(
                noFilters = "Нет фильтров",
                findSettlements = "Найти населённые пункты",
                withinCenter = { radius, center -> "в радиусе $radius км от $center" },
                aroundCenter = { center -> "вокруг $center" },
                atLeast = { value -> "не менее $value" },
                upTo = { value -> "не более $value" },
            )

        val text = FilterSummaryBuilder.build(request, mapOf(definition.id to definition), localized)

        assertTrue(text.startsWith("Найти населённые пункты"))
        assertTrue(text.contains("в радиусе 15 км от Минск"))
        assertTrue(text.contains("не более 5 km"))
    }

}
