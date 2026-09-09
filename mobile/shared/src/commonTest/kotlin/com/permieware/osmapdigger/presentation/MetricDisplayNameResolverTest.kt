package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.PreferredDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricDisplayNameResolverTest {
    @Test
    fun currentCategoryHasRussianPresentationLabel() {
        val definition = definition("railway_station", "Railway station")
        assertEquals("Ж/д станция — расстояние", MetricDisplayNameResolver.resolve(definition, UiLanguage.RUSSIAN))
        assertEquals("Ж/д станция", MetricDisplayNameResolver.resolveCompact(definition, UiLanguage.RUSSIAN))
        assertEquals("Railway station", MetricDisplayNameResolver.resolve(definition, UiLanguage.ENGLISH))
        assertEquals("Railway station", MetricDisplayNameResolver.resolveCompact(definition, UiLanguage.ENGLISH))
    }


    @Test
    fun countMetricIncludesMeasureAndRadiusInRussianLabel() {
        val definition =
            MetricDefinition(
                id = "industrial.count_5km",
                categoryId = "industrial",
                group = "Risks",
                title = "Industrial area",
                description = "",
                unit = "count",
                measureType = "count",
                preferredDirection = PreferredDirection.NEUTRAL,
                defaultEnabled = false,
                sortOrder = 2,
            )

        assertEquals(
            "Промышленная зона — количество в радиусе 5 км",
            MetricDisplayNameResolver.resolve(definition, UiLanguage.RUSSIAN),
        )
    }

    @Test
    fun unknownCategoryFallsBackToDatasetTitle() {
        val definition = definition("future_metric", "Future metric")
        assertEquals("Future metric", MetricDisplayNameResolver.resolve(definition, UiLanguage.RUSSIAN))
    }

    private fun definition(categoryId: String, title: String) =
        MetricDefinition(
            id = "$categoryId.distance_km",
            categoryId = categoryId,
            group = "Test",
            title = title,
            description = "",
            unit = "km",
            measureType = "distance",
            preferredDirection = PreferredDirection.LOWER,
            defaultEnabled = false,
            sortOrder = 1,
        )
}
