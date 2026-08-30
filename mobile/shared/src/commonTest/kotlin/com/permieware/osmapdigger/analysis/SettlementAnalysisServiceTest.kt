package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricPreferenceDefault
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.SearchRequest
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementAnalysisCandidate
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.domain.SettlementSearchEntry
import com.permieware.osmapdigger.dataset.GeoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettlementAnalysisServiceTest {
    @Test
    fun ranksAllEligibleCandidatesBeforeApplyingFinalLimit() = runTest {
        val repository =
            FakeRepository(
                candidates =
                    listOf(
                        candidate("early", "Alpha", forestDistance = 9.0),
                        candidate("best", "Zulu", forestDistance = 1.0),
                    ),
            )
        val service = SettlementAnalysisService(repository)

        val result =
            service.analyze(
                SettlementAnalysisRequest(
                    search = SearchRequest(limit = 1),
                    preferences = listOf(forestPreference()),
                ),
            )

        assertEquals(listOf("best"), result.map { it.settlement.id })
        assertEquals(setOf("forest.distance_km"), repository.requestedScoringMetricIds)
        assertEquals(1, repository.analysisCalls)
        assertEquals(0, repository.detailsCalls)
    }

    @Test
    fun exactRadiusFilteringHappensBeforeScoring() = runTest {
        val center = settlement("center", "Center", latitude = 0.0)
        val repository =
            FakeRepository(
                candidates =
                    listOf(
                        candidate("near", "Near", latitude = 0.01, forestDistance = 5.0),
                        candidate("far", "Far", latitude = 0.05, forestDistance = 1.0),
                    ),
            )
        val service = SettlementAnalysisService(repository)

        val result =
            service.analyze(
                SettlementAnalysisRequest(
                    search = SearchRequest(center = center, radiusKm = 2.0, limit = 10),
                    preferences = listOf(forestPreference()),
                ),
            )

        assertEquals(listOf("near"), result.map { it.settlement.id })
        assertTrue(repository.latitudeRange != null)
        assertTrue(repository.longitudeRange != null)
    }

    @Test
    fun onlyEffectiveHardConditionsArePassedToRepository() = runTest {
        val repository = FakeRepository(candidates = emptyList())
        val service = SettlementAnalysisService(repository)
        val effective = SearchCondition("water.distance_km", maxValue = 5.0)

        service.analyze(
            SettlementAnalysisRequest(
                search =
                    SearchRequest(
                        conditions =
                            listOf(
                                SearchCondition("forest.distance_km"),
                                effective,
                            ),
                    ),
                preferences = emptyList(),
            ),
        )

        assertEquals(listOf(effective), repository.conditions)
        assertEquals(emptySet(), repository.requestedScoringMetricIds)
    }

    @Test
    fun missingScoringMetricRemainsUnknownThroughAnalysis() = runTest {
        val repository =
            FakeRepository(
                candidates =
                    listOf(
                        SettlementAnalysisCandidate(
                            settlement = settlement("missing", "Missing"),
                            metricValues = emptyMap(),
                        ),
                    ),
            )
        val result =
            SettlementAnalysisService(repository).analyze(
                SettlementAnalysisRequest(
                    search = SearchRequest(limit = 10),
                    preferences = listOf(forestPreference()),
                ),
            )

        assertEquals(null, result.single().score.value)
        assertEquals(0.0, result.single().score.coverage)
        assertEquals(false, result.single().score.contributions.single().isKnown)
    }

    @Test
    fun invalidRadiusIsRejectedBeforeRepositoryAccess() = runTest {
        val repository = FakeRepository(candidates = emptyList())
        val service = SettlementAnalysisService(repository)

        val failure =
            try {
                service.analyze(
                    SettlementAnalysisRequest(
                        search = SearchRequest(radiusKm = 5.0),
                        preferences = emptyList(),
                    ),
                )
                null
            } catch (error: Throwable) {
                error
            }

        assertIs<IllegalArgumentException>(failure)
        assertEquals(0, repository.analysisCalls)
    }


    @Test
    fun importedCandidateScopeRestrictsRepositoryBeforeScoring() = runTest {
        val repository =
            FakeRepository(
                candidates =
                    listOf(
                        candidate("a", "Alpha", forestDistance = 4.0),
                        candidate("b", "Beta", forestDistance = 1.0),
                        candidate("c", "Gamma", forestDistance = 2.0),
                    ),
            )

        val result =
            SettlementAnalysisService(repository).analyze(
                SettlementAnalysisRequest(
                    search = SearchRequest(limit = 10),
                    preferences = listOf(forestPreference()),
                    candidateScope = SettlementCandidateScope.Imported(listOf("a", "c")),
                ),
            )

        assertEquals(setOf("a", "c"), repository.requestedCandidateSettlementIds)
        assertEquals(listOf("c", "a"), result.map { it.settlement.id })
        assertEquals(1, repository.analysisCalls)
    }

    @Test
    fun emptyImportedCandidateScopeDoesNotBroadenOrQueryRepository() = runTest {
        val repository =
            FakeRepository(
                candidates = listOf(candidate("a", "Alpha", forestDistance = 1.0)),
            )

        val outcome =
            SettlementAnalysisService(repository).analyzeWithDiagnostics(
                SettlementAnalysisRequest(
                    search = SearchRequest(limit = 10),
                    preferences = listOf(forestPreference()),
                    candidateScope = SettlementCandidateScope.Imported(emptyList()),
                ),
            )

        assertTrue(outcome.results.isEmpty())
        assertEquals(0, outcome.diagnostics.candidateCount)
        assertEquals(0, repository.analysisCalls)
    }

    @Test
    fun diagnosticsReportCandidateVolumeAndNonNegativeTimings() = runTest {
        val repository =
            FakeRepository(
                candidates =
                    listOf(
                        candidate("near", "Near", latitude = 0.01, forestDistance = 2.0),
                        candidate("far", "Far", latitude = 0.20, forestDistance = 1.0),
                    ),
            )

        val outcome =
            SettlementAnalysisService(repository).analyzeWithDiagnostics(
                SettlementAnalysisRequest(
                    search = SearchRequest(center = settlement("center", "Center"), radiusKm = 5.0, limit = 10),
                    preferences = listOf(forestPreference()),
                ),
            )

        assertEquals(2, outcome.diagnostics.candidateCount)
        assertEquals(1, outcome.diagnostics.exactEligibleCandidateCount)
        assertEquals(1, outcome.diagnostics.enabledScoringMetricCount)
        assertEquals(1, outcome.diagnostics.resultCount)
        assertTrue(outcome.diagnostics.batchRetrievalMillis >= 0.0)
        assertTrue(outcome.diagnostics.sharedScoringSortMillis >= 0.0)
        assertTrue(outcome.diagnostics.totalMillis >= 0.0)
        assertTrue(outcome.diagnostics.totalMillis >= outcome.diagnostics.batchRetrievalMillis)
    }

    private fun forestPreference() =
        MetricPreference(
            metricId = "forest.distance_km",
            direction = PreferredDirection.LOWER,
            targetValue = 2.0,
            limitValue = 10.0,
            weight = 5,
        )

    private fun candidate(
        id: String,
        name: String,
        latitude: Double = 0.0,
        forestDistance: Double,
    ) =
        SettlementAnalysisCandidate(
            settlement = settlement(id, name, latitude),
            metricValues = mapOf("forest.distance_km" to forestDistance),
        )

    private fun settlement(
        id: String,
        name: String,
        latitude: Double = 0.0,
    ) =
        Settlement(
            id = id,
            name = name,
            localName = null,
            englishName = null,
            placeType = "village",
            population = null,
            location = GeoPoint(latitude, 0.0),
        )

    private class FakeRepository(
        private val candidates: List<SettlementAnalysisCandidate>,
    ) : GeoRepository {
        var analysisCalls: Int = 0
        var detailsCalls: Int = 0
        var conditions: List<SearchCondition> = emptyList()
        var latitudeRange: ClosedFloatingPointRange<Double>? = null
        var longitudeRange: ClosedFloatingPointRange<Double>? = null
        var requestedScoringMetricIds: Set<String> = emptySet()
        var requestedCandidateSettlementIds: Set<String>? = null

        override suspend fun datasetInfo(): DatasetInfo =
            DatasetInfo("test", "Test", null, GeoPoint(0.0, 0.0), 10.0, false, null, "property")

        override suspend fun metricDefinitions(): List<MetricDefinition> = emptyList()

        override suspend fun preferenceDefaults(): List<MetricPreferenceDefault> = emptyList()

        override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> = emptyList()

        override suspend fun searchCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            limit: Int,
        ): List<Settlement> = error("Legacy search path must not be used by ranked analysis")

        override suspend fun analysisCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            scoringMetricIds: Set<String>,
            candidateSettlementIds: Set<String>?,
        ): List<SettlementAnalysisCandidate> {
            analysisCalls += 1
            this.conditions = conditions
            this.latitudeRange = latitudeRange
            this.longitudeRange = longitudeRange
            requestedScoringMetricIds = scoringMetricIds
            requestedCandidateSettlementIds = candidateSettlementIds
            return if (candidateSettlementIds == null) {
                candidates
            } else {
                candidates.filter { it.settlement.id in candidateSettlementIds }
            }
        }

        override suspend fun details(settlementId: String): SettlementDetails {
            detailsCalls += 1
            error("Per-candidate details hydration must not be used by ranked analysis")
        }
    }
}
