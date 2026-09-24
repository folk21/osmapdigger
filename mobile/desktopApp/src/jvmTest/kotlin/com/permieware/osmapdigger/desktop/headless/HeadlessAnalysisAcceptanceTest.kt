package com.permieware.osmapdigger.desktop.headless

import com.permieware.osmapdigger.analysis.SettlementAnalysisDiagnostics
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class HeadlessAnalysisAcceptanceTest {
    @Test
    fun representativeScoredDiagnosticsPassAndRenderCopyReadyMarkdown() {
        val diagnostics = diagnostics(candidates = 22_771, scoringMetrics = 7)

        validateAnalysisAcceptance(diagnostics, minCandidates = 1_000)
        val rendered = renderAnalysisAcceptanceMarkdown("belarus", diagnostics, minCandidates = 1_000)

        assertContains(rendered, "Dataset: `belarus`")
        assertContains(rendered, "Candidates: `22771`")
        assertContains(rendered, "Enabled scoring metrics: `7`")
        assertContains(rendered, "End-to-end analysis: `52.625 ms`")
    }

    @Test
    fun candidateThresholdAndScoringAreStrictAcceptanceRequirements() {
        assertFailsWith<IllegalStateException> {
            validateAnalysisAcceptance(diagnostics(candidates = 999, scoringMetrics = 7), minCandidates = 1_000)
        }
        assertFailsWith<IllegalStateException> {
            validateAnalysisAcceptance(diagnostics(candidates = 5_000, scoringMetrics = 0), minCandidates = 1_000)
        }
    }

    private fun diagnostics(candidates: Int, scoringMetrics: Int) =
        SettlementAnalysisDiagnostics(
            candidateCount = candidates,
            exactEligibleCandidateCount = candidates,
            enabledScoringMetricCount = scoringMetrics,
            resultCount = minOf(candidates, 100),
            batchRetrievalMillis = 42.125,
            sharedScoringSortMillis = 10.5,
            totalMillis = 52.625,
        )
}
