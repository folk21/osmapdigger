package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.runtime.GeoRepository
import com.permieware.osmapdigger.search.SearchRequestSemantics

/** Executes hard eligibility filtering followed by deterministic shared preference ranking. */
class SettlementAnalysisService(
    private val repository: GeoRepository,
) {
    /**
     * Analyze one request without allowing repository ordering to truncate ranked results.
     *
     * Platform SQL applies hard metric conditions and an optional coarse geographic box,
     * then returns only the active scoring metric values in batch. Shared code performs
     * exact radius filtering, preference scoring, deterministic ranking, and only then
     * applies the user-visible result limit.
     */
    suspend fun analyze(request: SettlementAnalysisRequest): List<ScoredSettlement> {
        val search = request.search
        SearchRequestSemantics.validate(search)

        val bounds = SearchRequestSemantics.boundingBox(search)
        val scoringMetricIds = request.preferences.mapTo(linkedSetOf()) { it.metricId }
        val candidates =
            repository.analysisCandidates(
                conditions = SearchRequestSemantics.effectiveConditions(search),
                latitudeRange = bounds?.first,
                longitudeRange = bounds?.second,
                scoringMetricIds = scoringMetricIds,
            )

        val scored =
            candidates
                .asSequence()
                .filter { SearchRequestSemantics.matchesExactRadius(search, it.settlement) }
                .map { candidate ->
                    ScoredSettlement(
                        settlement = candidate.settlement,
                        score = PreferenceScorer.score(request.preferences, candidate.metricValues),
                    )
                }
                .toList()

        return SettlementRanker
            .rank(scored)
            .take(SearchRequestSemantics.resultLimit(search))
    }
}
