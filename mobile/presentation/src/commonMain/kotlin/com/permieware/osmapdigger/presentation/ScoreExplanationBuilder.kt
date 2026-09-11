package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.analysis.PreferenceContribution
import com.permieware.osmapdigger.analysis.SettlementScore
import com.permieware.osmapdigger.domain.MetricDefinition

/** Mean normalized quality per Preference across the complete current eligible candidate set. */
data class PreferenceQualityBaseline(
    val averageQualityByMetricId: Map<String, Double>,
) {
    init {
        averageQualityByMetricId.forEach { (metricId, average) ->
            require(metricId.isNotBlank())
            require(average.isFinite() && average in 0.0..1.0)
        }
    }
}

/** Presentation-ready comparative explanation of a settlement preference score. */
data class ScoreExplanation(
    val advantage: ExplainedPreferenceContribution?,
    val compromise: ExplainedPreferenceContribution?,
    val unknown: List<ExplainedPreferenceContribution>,
)

/** One score contribution enriched with persisted metric presentation metadata and candidate-set context. */
data class ExplainedPreferenceContribution(
    val contribution: PreferenceContribution,
    val title: String,
    val unit: String,
    val averageQuality: Double?,
    val comparativeImpact: Double?,
)

/**
 * Build candidate-relative explanation cues without changing the authoritative absolute score.
 *
 * The baseline is computed from every eligible candidate before the visible result limit. A cue is
 * selected by weighted deviation from the candidate-set mean quality for the same Preference. This
 * prevents a high-weight metric that is equally good for nearly every candidate from being presented
 * as the main reason one settlement ranks above another.
 */
object ScoreExplanationBuilder {
    fun baseline(scores: Iterable<SettlementScore>): PreferenceQualityBaseline {
        val sums = linkedMapOf<String, Double>()
        val counts = linkedMapOf<String, Int>()
        scores.forEach { score ->
            score.contributions.forEach { contribution ->
                val quality = contribution.quality
                if (quality != null) {
                    sums[contribution.metricId] = (sums[contribution.metricId] ?: 0.0) + quality
                    counts[contribution.metricId] = (counts[contribution.metricId] ?: 0) + 1
                }
            }
        }
        val averages =
            sums.mapValues { (metricId, sum) ->
                sum / checkNotNull(counts[metricId])
            }
        return PreferenceQualityBaseline(averages)
    }

    fun build(
        score: SettlementScore,
        definitions: Map<String, MetricDefinition>,
        baseline: PreferenceQualityBaseline,
    ): ScoreExplanation {
        val explained =
            score.contributions.map { contribution ->
                val definition = definitions[contribution.metricId]
                val averageQuality = baseline.averageQualityByMetricId[contribution.metricId]
                val quality = contribution.quality
                val comparativeImpact =
                    if (score.totalWeight == 0 || quality == null || averageQuality == null) {
                        null
                    } else {
                        SCORE_SCALE * contribution.weight.toDouble() / score.totalWeight *
                            (quality - averageQuality)
                    }
                ExplainedPreferenceContribution(
                    contribution = contribution,
                    title = definition?.title ?: contribution.metricId,
                    unit = definition?.unit.orEmpty(),
                    averageQuality = averageQuality,
                    comparativeImpact = comparativeImpact,
                )
            }

        val known = explained.filter { it.contribution.isKnown && it.comparativeImpact != null }
        val advantage =
            known
                .filter { (it.comparativeImpact ?: 0.0) > COMPARATIVE_EPSILON }
                .maxWithOrNull(
                    compareBy<ExplainedPreferenceContribution> { it.comparativeImpact ?: Double.NEGATIVE_INFINITY }
                        .thenByDescending { it.contribution.metricId },
                )
        val compromise =
            known
                .filter { (it.comparativeImpact ?: 0.0) < -COMPARATIVE_EPSILON }
                .minWithOrNull(
                    compareBy<ExplainedPreferenceContribution> { it.comparativeImpact ?: Double.POSITIVE_INFINITY }
                        .thenBy { it.contribution.metricId },
                )
        val unknown = explained.filterNot { it.contribution.isKnown }.sortedBy { it.contribution.metricId }
        return ScoreExplanation(
            advantage = advantage,
            compromise = compromise,
            unknown = unknown,
        )
    }

    private const val SCORE_SCALE = 100.0
    private const val COMPARATIVE_EPSILON = 1e-9
}
