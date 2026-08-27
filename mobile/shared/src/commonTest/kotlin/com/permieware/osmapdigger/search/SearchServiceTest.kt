package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.SettlementAnalysisCandidate
import com.permieware.osmapdigger.domain.SearchRequest
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.domain.SettlementSearchEntry
import com.permieware.osmapdigger.runtime.GeoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SearchServiceTest {
    @Test
    fun radiusChangesExactResultSet() = runTest {
        val center = settlement("center", 0.0, 0.0)
        val near = settlement("near", 0.01, 0.0)
        val farther = settlement("farther", 0.05, 0.0)
        val service = SearchService(FakeRepository(listOf(center, near, farther)))

        val withinTwo = service.search(SearchRequest(center = center, radiusKm = 2.0, limit = 10))
        val withinFifteen = service.search(SearchRequest(center = center, radiusKm = 15.0, limit = 10))

        assertEquals(listOf("center", "near"), withinTwo.map { it.id })
        assertEquals(listOf("center", "near", "farther"), withinFifteen.map { it.id })
    }

    @Test
    fun radiusWithoutCenterIsRejectedInsteadOfSilentlyIgnored() = runTest {
        val service = SearchService(FakeRepository(emptyList()))

        val failure =
            try {
                service.search(SearchRequest(radiusKm = 2.0))
                null
            } catch (error: Throwable) {
                error
            }

        assertIs<IllegalArgumentException>(failure)
    }

    private fun settlement(id: String, latitude: Double, longitude: Double) =
        Settlement(
            id = id,
            name = id,
            localName = null,
            englishName = null,
            placeType = "village",
            population = null,
            location = GeoPoint(latitude, longitude),
        )

    private class FakeRepository(
        private val settlements: List<Settlement>,
    ) : GeoRepository {
        override suspend fun datasetInfo(): DatasetInfo =
            DatasetInfo("test", "Test", null, GeoPoint(0.0, 0.0), 10.0, false, null, "property")

        override suspend fun metricDefinitions(): List<MetricDefinition> = emptyList()

        override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> = emptyList()

        override suspend fun searchCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            limit: Int,
        ): List<Settlement> = settlements.take(limit)

        override suspend fun analysisCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            scoringMetricIds: Set<String>,
        ): List<SettlementAnalysisCandidate> = error("not used")

        override suspend fun details(settlementId: String): SettlementDetails =
            SettlementDetails(settlements.first { it.id == settlementId }, emptyList())
    }
}
