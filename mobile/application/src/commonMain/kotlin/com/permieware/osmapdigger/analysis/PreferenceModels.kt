package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.Settlement

/** One enabled numeric preference used to rank otherwise eligible settlements. */
data class MetricPreference(
    val metricId: String,
    val direction: PreferredDirection,
    val targetValue: Double,
    val limitValue: Double,
    val weight: Int,
) {
    init {
        require(metricId.isNotBlank()) { "Preference metric ID must not be blank" }
        require(direction != PreferredDirection.NEUTRAL) {
            "Neutral metric requires an explicit scoreable direction"
        }
        require(targetValue.isFinite() && limitValue.isFinite()) {
            "Preference target and limit must be finite"
        }
        require(weight in WEIGHT_RANGE) { "Preference weight must be in $WEIGHT_RANGE" }

        when (direction) {
            PreferredDirection.LOWER ->
                require(targetValue < limitValue) {
                    "Lower-is-better preference requires target < limit"
                }

            PreferredDirection.HIGHER ->
                require(targetValue > limitValue) {
                    "Higher-is-better preference requires target > limit"
                }

            PreferredDirection.NEUTRAL -> error("Neutral direction is rejected above")
        }
    }

    companion object {
        val WEIGHT_RANGE: IntRange = 1..10
    }
}

/** Deterministic explanation of one preference's contribution to a settlement score. */
data class PreferenceContribution(
    val metricId: String,
    val direction: PreferredDirection,
    val targetValue: Double,
    val limitValue: Double,
    val weight: Int,
    val rawValue: Double?,
    val quality: Double?,
    val weightedContribution: Double?,
    val scoreContribution: Double?,
) {
    val isKnown: Boolean
        get() = rawValue != null
}

/** Aggregate preference score and weighted metric-data coverage for one settlement. */
data class SettlementScore(
    val value: Double?,
    val coverage: Double,
    val knownWeight: Int,
    val totalWeight: Int,
    val contributions: List<PreferenceContribution>,
)

/** One settlement paired with its already calculated preference score. */
data class ScoredSettlement(
    val settlement: Settlement,
    val score: SettlementScore,
)
