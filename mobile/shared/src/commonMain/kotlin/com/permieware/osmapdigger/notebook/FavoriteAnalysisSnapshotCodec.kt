package com.permieware.osmapdigger.notebook

import com.permieware.osmapdigger.domain.PreferredDirection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned JSON codec for one favorite analysis snapshot row. */
object FavoriteAnalysisSnapshotCodec {
    private const val CURRENT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: FavoriteAnalysisSnapshot): String =
        json.encodeToString(snapshot.toPayload())

    fun decode(payload: String): FavoriteAnalysisSnapshot {
        val decoded = json.decodeFromString<SnapshotPayload>(payload)
        require(decoded.version == CURRENT_VERSION) {
            "Unsupported favorite analysis snapshot version: ${decoded.version}"
        }
        return decoded.toDomain()
    }

    @Serializable
    private data class SnapshotPayload(
        val version: Int,
        val datasetId: String,
        val settlementId: String,
        val settlementName: String,
        val capturedAtEpochMs: Long,
        val scoreValue: Double? = null,
        val coverage: Double,
        val knownWeight: Int,
        val totalWeight: Int,
        val centerSettlementId: String? = null,
        val centerSettlementName: String? = null,
        val radiusKm: Double? = null,
        val requiredCriteria: List<RequiredPayload>,
        val preferences: List<PreferencePayload>,
    )

    @Serializable
    private data class RequiredPayload(
        val metricId: String,
        val title: String,
        val unit: String,
        val minValue: Double? = null,
        val maxValue: Double? = null,
    )

    @Serializable
    private data class PreferencePayload(
        val metricId: String,
        val title: String,
        val unit: String,
        val direction: String,
        val targetValue: Double,
        val limitValue: Double,
        val weight: Int,
        val rawValue: Double? = null,
        val quality: Double? = null,
        val weightedContribution: Double? = null,
        val scoreContribution: Double? = null,
    )

    private fun FavoriteAnalysisSnapshot.toPayload() = SnapshotPayload(
        version = CURRENT_VERSION,
        datasetId = datasetId,
        settlementId = settlementId,
        settlementName = settlementName,
        capturedAtEpochMs = capturedAtEpochMs,
        scoreValue = scoreValue,
        coverage = coverage,
        knownWeight = knownWeight,
        totalWeight = totalWeight,
        centerSettlementId = centerSettlementId,
        centerSettlementName = centerSettlementName,
        radiusKm = radiusKm,
        requiredCriteria = requiredCriteria.map {
            RequiredPayload(it.metricId, it.title, it.unit, it.minValue, it.maxValue)
        },
        preferences = preferences.map {
            PreferencePayload(
                metricId = it.metricId,
                title = it.title,
                unit = it.unit,
                direction = it.direction.name,
                targetValue = it.targetValue,
                limitValue = it.limitValue,
                weight = it.weight,
                rawValue = it.rawValue,
                quality = it.quality,
                weightedContribution = it.weightedContribution,
                scoreContribution = it.scoreContribution,
            )
        },
    )

    private fun SnapshotPayload.toDomain() = FavoriteAnalysisSnapshot(
        datasetId = datasetId,
        settlementId = settlementId,
        settlementName = settlementName,
        capturedAtEpochMs = capturedAtEpochMs,
        scoreValue = scoreValue,
        coverage = coverage,
        knownWeight = knownWeight,
        totalWeight = totalWeight,
        centerSettlementId = centerSettlementId,
        centerSettlementName = centerSettlementName,
        radiusKm = radiusKm,
        requiredCriteria = requiredCriteria.map {
            FavoriteRequiredCriterionSnapshot(it.metricId, it.title, it.unit, it.minValue, it.maxValue)
        },
        preferences = preferences.map {
            FavoritePreferenceSnapshot(
                metricId = it.metricId,
                title = it.title,
                unit = it.unit,
                direction = PreferredDirection.valueOf(it.direction),
                targetValue = it.targetValue,
                limitValue = it.limitValue,
                weight = it.weight,
                rawValue = it.rawValue,
                quality = it.quality,
                weightedContribution = it.weightedContribution,
                scoreContribution = it.scoreContribution,
            )
        },
    )
}
