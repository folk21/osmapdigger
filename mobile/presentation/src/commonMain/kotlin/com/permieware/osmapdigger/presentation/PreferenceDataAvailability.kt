package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.analysis.SettlementScore
import com.permieware.osmapdigger.domain.MetricDefinition

/** Data-availability summary for the enabled Preferences represented by one settlement score. */
data class PreferenceDataAvailability(
    val totalPreferenceCount: Int,
    val knownPreferenceCount: Int,
    val missingMetricIds: List<String>,
) {
    init {
        require(totalPreferenceCount >= 0)
        require(knownPreferenceCount in 0..totalPreferenceCount)
        require(missingMetricIds.size == totalPreferenceCount - knownPreferenceCount)
    }

    val missingPreferenceCount: Int
        get() = missingMetricIds.size

    val isScoreUnavailableBecauseAllPreferenceDataIsMissing: Boolean
        get() = totalPreferenceCount > 0 && knownPreferenceCount == 0
}

/** Derives missing Preference data directly from the authoritative score contributions. */
object PreferenceDataAvailabilityBuilder {
    fun build(score: SettlementScore): PreferenceDataAvailability {
        val missingMetricIds =
            score.contributions
                .asSequence()
                .filterNot { it.isKnown }
                .map { it.metricId }
                .sorted()
                .toList()
        return PreferenceDataAvailability(
            totalPreferenceCount = score.contributions.size,
            knownPreferenceCount = score.contributions.size - missingMetricIds.size,
            missingMetricIds = missingMetricIds,
        )
    }
}

/** Resolves missing Preference metric IDs to stable localized presentation labels. */
object PreferenceDataAvailabilityPresenter {
    fun missingTitles(
        availability: PreferenceDataAvailability,
        definitions: Map<String, MetricDefinition>,
        language: UiLanguage,
    ): List<String> =
        availability.missingMetricIds.map { metricId ->
            definitions[metricId]?.let { MetricDisplayNameResolver.resolve(it, language) } ?: metricId
        }
}
