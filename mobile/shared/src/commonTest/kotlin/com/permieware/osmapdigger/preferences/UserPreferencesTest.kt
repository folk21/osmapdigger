package com.permieware.osmapdigger.preferences

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricPreferenceDefault
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserPreferencesTest {
    @Test
    fun filterPayloadRoundTripsDynamicConditions() {
        val conditions =
            listOf(
                SearchCondition("forest.distance_km", maxValue = 2.0),
                SearchCondition("farmyard.distance_km", minValue = 5.0),
                SearchCondition("water.distance_km"),
            )

        val encoded = SearchConditionPayloadCodec.encode(conditions)

        assertEquals(conditions, SearchConditionPayloadCodec.decode(encoded))
    }

    @Test
    fun malformedOrUnsupportedFilterPayloadIsRejected() {
        assertNull(SearchConditionPayloadCodec.decode("not-json"))
        assertNull(SearchConditionPayloadCodec.decode("""{"version":2,"filters":[]}"""))
    }

    @Test
    fun restoreIgnoresUnknownMetricsAndDoesNotCrossDatasets() = runTest {
        val preferences =
            UserPreferences(
                datasetId = "dataset-a",
                conditions =
                    listOf(
                        SearchCondition("known", maxValue = 3.0),
                        SearchCondition("removed", minValue = 1.0),
                    ),
            )

        val wrongDataset =
            UserPreferencesRestorer.restore(
                preferences = preferences,
                datasetId = "dataset-b",
                definitions = listOf(metric("known")),
                resolveSettlement = { null },
            )
        assertNull(wrongDataset)

        val restored =
            UserPreferencesRestorer.restore(
                preferences = preferences,
                datasetId = "dataset-a",
                definitions = listOf(metric("known")),
                resolveSettlement = { null },
            )

        assertEquals(listOf(SearchCondition("known", maxValue = 3.0)), restored?.conditions)
    }

    @Test
    fun radiusWithoutCenterIsNotRestored() = runTest {
        val preferences =
            UserPreferences(
                datasetId = "dataset-a",
                radiusKm = 15.0,
            )

        val restored =
            UserPreferencesRestorer.restore(
                preferences = preferences,
                datasetId = "dataset-a",
                definitions = emptyList(),
                resolveSettlement = { null },
            )

        assertNull(restored?.center)
        assertNull(restored?.radiusKm)
    }

    @Test
    fun unavailableCenterClearsCenterAndRadiusWithoutNameFallback() = runTest {
        val preferences =
            UserPreferences(
                datasetId = "dataset-a",
                centerSettlementId = "node:10",
                centerSettlementName = "Old display name",
                radiusKm = 25.0,
                conditions = listOf(SearchCondition("known")),
            )

        val restored =
            UserPreferencesRestorer.restore(
                preferences = preferences,
                datasetId = "dataset-a",
                definitions = listOf(metric("known")),
                resolveSettlement = { null },
            )

        assertNull(restored?.center)
        assertNull(restored?.radiusKm)
    }

    @Test
    fun availableCenterRestoresByStableId() = runTest {
        val center =
            Settlement(
                id = "node:10",
                name = "Current display name",
                localName = null,
                englishName = null,
                placeType = "village",
                population = null,
                location = GeoPoint(42.5, 1.5),
            )
        val preferences =
            UserPreferences(
                datasetId = "dataset-a",
                centerSettlementId = center.id,
                centerSettlementName = "Old display name",
                radiusKm = 25.0,
            )

        val restored =
            UserPreferencesRestorer.restore(
                preferences = preferences,
                datasetId = "dataset-a",
                definitions = emptyList(),
                resolveSettlement = { id -> if (id == center.id) center else null },
            )

        assertEquals(center, restored?.center)
        assertEquals(25.0, restored?.radiusKm)
    }

    @Test
    fun preferenceOverridePayloadRoundTripsPartialOverrides() {
        val overrides =
            listOf(
                MetricPreferenceOverride(
                    metricId = "forest.distance_km",
                    enabled = false,
                    weight = 9,
                ),
                MetricPreferenceOverride(
                    metricId = "water.distance_km",
                    targetValue = 0.5,
                    limitValue = 8.0,
                ),
            )

        val encoded = MetricPreferenceOverridePayloadCodec.encode(overrides)

        assertEquals(overrides, MetricPreferenceOverridePayloadCodec.decode(encoded))
    }

    @Test
    fun malformedUnsupportedOrInvalidPreferencePayloadIsRejected() {
        assertNull(MetricPreferenceOverridePayloadCodec.decode("not-json"))
        assertNull(
            MetricPreferenceOverridePayloadCodec.decode(
                """{"version":2,"preferences":[]}""",
            ),
        )
        assertNull(
            MetricPreferenceOverridePayloadCodec.decode(
                """{"version":1,"preferences":[{"metricId":"forest.distance_km","weight":99}]}""",
            ),
        )
    }

    @Test
    fun datasetDefaultsAndPartialOverridesResolveDeterministically() {
        val defaults =
            listOf(
                preferenceDefault(
                    metricId = "forest.distance_km",
                    direction = PreferredDirection.LOWER,
                    target = 1.0,
                    limit = 10.0,
                    weight = 8,
                    enabled = true,
                ),
                preferenceDefault(
                    metricId = "industrial.distance_km",
                    direction = PreferredDirection.HIGHER,
                    target = 10.0,
                    limit = 2.0,
                    weight = 7,
                    enabled = true,
                ),
            )

        val resolved =
            MetricPreferenceOverrideResolver.resolve(
                defaults = defaults,
                overrides =
                    listOf(
                        MetricPreferenceOverride(
                            metricId = "forest.distance_km",
                            enabled = false,
                            weight = 10,
                        ),
                        MetricPreferenceOverride(
                            metricId = "removed.distance_km",
                            weight = 5,
                        ),
                    ),
            )

        assertEquals(2, resolved.size)
        assertFalse(resolved[0].enabled)
        assertEquals(1.0, resolved[0].preference.targetValue)
        assertEquals(10.0, resolved[0].preference.limitValue)
        assertEquals(10, resolved[0].preference.weight)
        assertTrue(resolved[1].enabled)
        assertEquals(7, resolved[1].preference.weight)
    }

    @Test
    fun preferenceOverridesDoNotCrossDatasets() {
        val defaults =
            listOf(
                preferenceDefault(
                    metricId = "forest.distance_km",
                    direction = PreferredDirection.LOWER,
                    target = 1.0,
                    limit = 10.0,
                    weight = 8,
                    enabled = true,
                ),
            )
        val saved =
            UserPreferences(
                datasetId = "dataset-a",
                preferenceOverrides =
                    listOf(MetricPreferenceOverride("forest.distance_km", weight = 10)),
            )

        assertNull(MetricPreferenceOverrideResolver.restore(saved, "dataset-b", defaults))
        assertEquals(
            10,
            MetricPreferenceOverrideResolver.restore(saved, "dataset-a", defaults)
                ?.single()
                ?.preference
                ?.weight,
        )
    }

    @Test
    fun invalidEffectiveOverrideAndDuplicateMetricIdsAreRejected() {
        val defaults =
            listOf(
                preferenceDefault(
                    metricId = "forest.distance_km",
                    direction = PreferredDirection.LOWER,
                    target = 1.0,
                    limit = 10.0,
                    weight = 8,
                    enabled = true,
                ),
            )

        assertFailsWith<IllegalArgumentException> {
            MetricPreferenceOverrideResolver.resolve(
                defaults,
                listOf(MetricPreferenceOverride("forest.distance_km", targetValue = 20.0)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MetricPreferenceOverrideResolver.resolve(
                defaults,
                listOf(
                    MetricPreferenceOverride("forest.distance_km", weight = 7),
                    MetricPreferenceOverride("forest.distance_km", enabled = false),
                ),
            )
        }
    }

    private fun preferenceDefault(
        metricId: String,
        direction: PreferredDirection,
        target: Double,
        limit: Double,
        weight: Int,
        enabled: Boolean,
    ) =
        MetricPreferenceDefault(
            metricId = metricId,
            direction = direction,
            targetValue = target,
            limitValue = limit,
            weight = weight,
            defaultEnabled = enabled,
        )

    private fun metric(id: String) =
        MetricDefinition(
            id = id,
            categoryId = "test",
            group = "Test",
            title = "Test metric",
            description = "Test metric",
            unit = "km",
            measureType = "distance",
            preferredDirection = PreferredDirection.LOWER,
            defaultEnabled = false,
            sortOrder = 0,
        )
}
