package com.permieware.osmapdigger.notebook

import com.permieware.osmapdigger.domain.PreferredDirection

/** Immutable analysis context captured explicitly for one favorite settlement. */
data class FavoriteAnalysisSnapshot(
    val datasetId: String,
    val settlementId: String,
    val settlementName: String,
    val capturedAtEpochMs: Long,
    val scoreValue: Double?,
    val coverage: Double,
    val knownWeight: Int,
    val totalWeight: Int,
    val centerSettlementId: String?,
    val centerSettlementName: String?,
    val radiusKm: Double?,
    val requiredCriteria: List<FavoriteRequiredCriterionSnapshot>,
    val preferences: List<FavoritePreferenceSnapshot>,
) {
    init {
        require(datasetId.isNotBlank())
        require(settlementId.isNotBlank())
        require(settlementName.isNotBlank())
        require(capturedAtEpochMs >= 0L)
        require(scoreValue == null || scoreValue.isFinite() && scoreValue in 0.0..100.0)
        require(coverage.isFinite() && coverage in 0.0..100.0)
        require(knownWeight >= 0)
        require(totalWeight >= knownWeight)
        require(radiusKm == null || radiusKm.isFinite() && radiusKm > 0.0)
        require((centerSettlementId == null) == (centerSettlementName == null)) {
            "Snapshot center ID and name must either both be present or both be absent"
        }
        require(requiredCriteria.map { it.metricId }.distinct().size == requiredCriteria.size)
        require(preferences.map { it.metricId }.distinct().size == preferences.size)
    }
}


/** Analysis content prepared in shared code before the platform repository assigns capture time. */
data class FavoriteAnalysisSnapshotDraft(
    val datasetId: String,
    val settlementId: String,
    val settlementName: String,
    val scoreValue: Double?,
    val coverage: Double,
    val knownWeight: Int,
    val totalWeight: Int,
    val centerSettlementId: String?,
    val centerSettlementName: String?,
    val radiusKm: Double?,
    val requiredCriteria: List<FavoriteRequiredCriterionSnapshot>,
    val preferences: List<FavoritePreferenceSnapshot>,
) {
    fun requireIdentity(datasetId: String, settlementId: String) {
        require(this.datasetId == datasetId) { "Snapshot dataset ID must match favorite dataset ID" }
        require(this.settlementId == settlementId) { "Snapshot settlement ID must match favorite settlement ID" }
    }

    fun capturedAt(epochMs: Long): FavoriteAnalysisSnapshot = FavoriteAnalysisSnapshot(
        datasetId = datasetId,
        settlementId = settlementId,
        settlementName = settlementName,
        capturedAtEpochMs = epochMs,
        scoreValue = scoreValue,
        coverage = coverage,
        knownWeight = knownWeight,
        totalWeight = totalWeight,
        centerSettlementId = centerSettlementId,
        centerSettlementName = centerSettlementName,
        radiusKm = radiusKm,
        requiredCriteria = requiredCriteria,
        preferences = preferences,
    )
}

/** Frozen hard-filter definition that participated in the captured analysis. */
data class FavoriteRequiredCriterionSnapshot(
    val metricId: String,
    val title: String,
    val unit: String,
    val minValue: Double?,
    val maxValue: Double?,
) {
    init {
        require(metricId.isNotBlank())
        require(title.isNotBlank())
        require(minValue == null || minValue.isFinite())
        require(maxValue == null || maxValue.isFinite())
        require(minValue == null || maxValue == null || minValue <= maxValue)
    }
}

/** Frozen preference configuration and contribution for one captured settlement score. */
data class FavoritePreferenceSnapshot(
    val metricId: String,
    val title: String,
    val unit: String,
    val direction: PreferredDirection,
    val targetValue: Double,
    val limitValue: Double,
    val weight: Int,
    val rawValue: Double?,
    val quality: Double?,
    val weightedContribution: Double?,
    val scoreContribution: Double?,
) {
    init {
        require(metricId.isNotBlank())
        require(title.isNotBlank())
        require(direction != PreferredDirection.NEUTRAL)
        require(targetValue.isFinite() && limitValue.isFinite())
        when (direction) {
            PreferredDirection.LOWER -> require(targetValue < limitValue)
            PreferredDirection.HIGHER -> require(targetValue > limitValue)
            PreferredDirection.NEUTRAL -> error("Neutral direction is rejected above")
        }
        require(weight in 1..10)
        require(rawValue == null || rawValue.isFinite())
        require(quality == null || quality.isFinite() && quality in 0.0..1.0)
        require(weightedContribution == null || weightedContribution.isFinite())
        require(scoreContribution == null || scoreContribution.isFinite())
    }
}
