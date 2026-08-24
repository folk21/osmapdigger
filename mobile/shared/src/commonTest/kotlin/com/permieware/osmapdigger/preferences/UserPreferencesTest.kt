package com.permieware.osmapdigger.preferences

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
