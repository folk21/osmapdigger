package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.runtime.GeoRepository
import com.permieware.osmapdigger.search.SearchRequestSemantics
import kotlin.time.TimeSource

/** Executes hard eligibility filtering followed by deterministic shared preference ranking. */
class SettlementAnalysisService(
    private val repository: GeoRepository,
) {
    /** Execute ranked analysis and return only semantic results. */
    suspend fun analyze(request: SettlementAnalysisRequest): List<ScoredSettlement> =
        analyzeWithDiagnostics(request).results

    /**
     * Analyze one request and capture execution-volume/timing diagnostics without changing semantics.
     *
     * Platform SQL applies hard metric conditions and an optional coarse geographic box,
     * then returns only the active scoring metric values in batch. Shared code performs
     * exact radius filtering, preference scoring, deterministic ranking, and only then
     * applies the user-visible result limit.
     */
    suspend fun analyzeWithDiagnostics(request: SettlementAnalysisRequest): SettlementAnalysisOutcome {
        val totalMark = TimeSource.Monotonic.markNow()
        val search = request.search
        SearchRequestSemantics.validate(search)

        val bounds = SearchRequestSemantics.boundingBox(search)
        val scoringMetricIds = request.preferences.mapTo(linkedSetOf()) { it.metricId }
        val retrievalMark = TimeSource.Monotonic.markNow()
        val candidates =
            repository.analysisCandidates(
                conditions = SearchRequestSemantics.effectiveConditions(search),
                latitudeRange = bounds?.first,
                longitudeRange = bounds?.second,
                scoringMetricIds = scoringMetricIds,
            )
        val batchRetrievalMillis = retrievalMark.elapsedNow().inWholeNanoseconds / 1_000_000.0

        val sharedMark = TimeSource.Monotonic.markNow()
        val exactEligible =
            candidates.filter { SearchRequestSemantics.matchesExactRadius(search, it.settlement) }
        val scored =
            exactEligible.map { candidate ->
                ScoredSettlement(
                    settlement = candidate.settlement,
                    score = PreferenceScorer.score(request.preferences, candidate.metricValues),
                )
            }
        val results =
            SettlementRanker
                .rank(scored)
                .take(SearchRequestSemantics.resultLimit(search))
        val sharedScoringSortMillis = sharedMark.elapsedNow().inWholeNanoseconds / 1_000_000.0
        val totalMillis = totalMark.elapsedNow().inWholeNanoseconds / 1_000_000.0

        return SettlementAnalysisOutcome(
            results = results,
            diagnostics =
                SettlementAnalysisDiagnostics(
                    candidateCount = candidates.size,
                    exactEligibleCandidateCount = exactEligible.size,
                    enabledScoringMetricCount = scoringMetricIds.size,
                    resultCount = results.size,
                    batchRetrievalMillis = batchRetrievalMillis,
                    sharedScoringSortMillis = sharedScoringSortMillis,
                    totalMillis = totalMillis,
                ),
        )
    }
}
