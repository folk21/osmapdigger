package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.analysis.PreferenceContribution
import com.permieware.osmapdigger.analysis.SettlementScore
import com.permieware.osmapdigger.domain.MetricDefinition

/** Presentation-ready deterministic explanation of a settlement preference score. */
data class ScoreExplanation(
    val strongest: ExplainedPreferenceContribution?,
    val weakest: ExplainedPreferenceContribution?,
    val unknown: List<ExplainedPreferenceContribution>,
)

/** One score contribution enriched with persisted metric presentation metadata. */
data class ExplainedPreferenceContribution(
    val contribution: PreferenceContribution,
    val title: String,
    val unit: String,
)

/** Convert structured score contributions into stable strongest/weakest/unknown presentation groups. */
object ScoreExplanationBuilder {
    fun build(
        score: SettlementScore,
        definitions: Map<String, MetricDefinition>,
    ): ScoreExplanation {
        val explained =
            score.contributions.map { contribution ->
                val definition = definitions[contribution.metricId]
                ExplainedPreferenceContribution(
                    contribution = contribution,
                    title = definition?.title ?: contribution.metricId,
                    unit = definition?.unit.orEmpty(),
                )
            }
        val known = explained.filter { it.contribution.isKnown }
        val strongest =
            known.maxWithOrNull(
                compareBy<ExplainedPreferenceContribution> {
                    it.contribution.scoreContribution ?: Double.NEGATIVE_INFINITY
                }.thenByDescending { it.contribution.metricId },
            )
        val weakest =
            known.minWithOrNull(
                compareBy<ExplainedPreferenceContribution> {
                    it.contribution.scoreContribution ?: Double.POSITIVE_INFINITY
                }.thenBy { it.contribution.metricId },
            )
        val unknown = explained.filterNot { it.contribution.isKnown }.sortedBy { it.contribution.metricId }
        return ScoreExplanation(
            strongest = strongest,
            weakest = weakest?.takeUnless { it.contribution.metricId == strongest?.contribution?.metricId },
            unknown = unknown,
        )
    }
}
