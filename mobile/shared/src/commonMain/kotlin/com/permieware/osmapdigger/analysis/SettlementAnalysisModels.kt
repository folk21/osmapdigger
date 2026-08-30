package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.domain.SearchRequest

/** Complete shared analysis request: hard eligibility search plus ranking preferences. */
data class SettlementAnalysisRequest(
    val search: SearchRequest,
    val preferences: List<MetricPreference>,
    val candidateScope: SettlementCandidateScope = SettlementCandidateScope.Dataset,
) {
    init {
        require(preferences.map { it.metricId }.distinct().size == preferences.size) {
            "Analysis preferences must use unique metric IDs"
        }
    }
}


/** Timing and volume diagnostics for one completed ranked-analysis generation. */
data class SettlementAnalysisDiagnostics(
    val candidateCount: Int,
    val exactEligibleCandidateCount: Int,
    val enabledScoringMetricCount: Int,
    val resultCount: Int,
    val batchRetrievalMillis: Double,
    val sharedScoringSortMillis: Double,
    val totalMillis: Double,
) {
    init {
        require(candidateCount >= 0)
        require(exactEligibleCandidateCount in 0..candidateCount)
        require(enabledScoringMetricCount >= 0)
        require(resultCount in 0..exactEligibleCandidateCount)
        require(batchRetrievalMillis.isFinite() && batchRetrievalMillis >= 0.0)
        require(sharedScoringSortMillis.isFinite() && sharedScoringSortMillis >= 0.0)
        require(totalMillis.isFinite() && totalMillis >= 0.0)
    }
}

/** Ranked-analysis result together with non-semantic execution diagnostics. */
data class SettlementAnalysisOutcome(
    val results: List<ScoredSettlement>,
    val diagnostics: SettlementAnalysisDiagnostics,
)
