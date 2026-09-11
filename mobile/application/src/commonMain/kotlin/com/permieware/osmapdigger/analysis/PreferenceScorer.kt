package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.domain.PreferredDirection

/** Pure deterministic preference scoring with explicit missing-data semantics. */
object PreferenceScorer {
    /**
     * Score one settlement from enabled preferences and available metric values.
     *
     * Missing map entries are unknown and are excluded from the score denominator. A
     * present metric value must be finite; non-finite persisted/runtime data is invalid
     * rather than another representation of missing data.
     */
    fun score(
        preferences: List<MetricPreference>,
        metricValues: Map<String, Double>,
    ): SettlementScore {
        require(preferences.map { it.metricId }.distinct().size == preferences.size) {
            "Enabled preferences must use unique metric IDs"
        }

        val totalWeight = preferences.sumOf { it.weight }
        val rawContributions =
            preferences.map { preference ->
                val rawValue = metricValues[preference.metricId]
                if (rawValue == null) {
                    RawContribution(preference = preference)
                } else {
                    require(rawValue.isFinite()) {
                        "Metric '${preference.metricId}' must be finite when present"
                    }
                    val quality = quality(preference, rawValue)
                    RawContribution(
                        preference = preference,
                        rawValue = rawValue,
                        quality = quality,
                        weightedContribution = preference.weight * quality,
                    )
                }
            }

        val knownWeight =
            rawContributions
                .filter { it.quality != null }
                .sumOf { it.preference.weight }
        val weightedTotal = rawContributions.sumOf { it.weightedContribution ?: 0.0 }
        val value =
            if (knownWeight == 0) {
                null
            } else {
                SCORE_SCALE * weightedTotal / knownWeight
            }
        val coverage =
            if (totalWeight == 0) {
                0.0
            } else {
                SCORE_SCALE * knownWeight / totalWeight
            }

        val contributions =
            rawContributions.map { contribution ->
                val preference = contribution.preference
                PreferenceContribution(
                    metricId = preference.metricId,
                    direction = preference.direction,
                    targetValue = preference.targetValue,
                    limitValue = preference.limitValue,
                    weight = preference.weight,
                    rawValue = contribution.rawValue,
                    quality = contribution.quality,
                    weightedContribution = contribution.weightedContribution,
                    scoreContribution =
                        contribution.weightedContribution?.let { weighted ->
                            SCORE_SCALE * weighted / knownWeight
                        },
                )
            }

        return SettlementScore(
            value = value,
            coverage = coverage,
            knownWeight = knownWeight,
            totalWeight = totalWeight,
            contributions = contributions,
        )
    }

    private fun quality(
        preference: MetricPreference,
        value: Double,
    ): Double =
        when (preference.direction) {
            PreferredDirection.LOWER ->
                when {
                    value <= preference.targetValue -> 1.0
                    value >= preference.limitValue -> 0.0
                    else ->
                        (preference.limitValue - value) /
                            (preference.limitValue - preference.targetValue)
                }

            PreferredDirection.HIGHER ->
                when {
                    value >= preference.targetValue -> 1.0
                    value <= preference.limitValue -> 0.0
                    else ->
                        (value - preference.limitValue) /
                            (preference.targetValue - preference.limitValue)
                }

            PreferredDirection.NEUTRAL -> error("MetricPreference rejects neutral direction")
        }

    private data class RawContribution(
        val preference: MetricPreference,
        val rawValue: Double? = null,
        val quality: Double? = null,
        val weightedContribution: Double? = null,
    )

    private const val SCORE_SCALE = 100.0
}
