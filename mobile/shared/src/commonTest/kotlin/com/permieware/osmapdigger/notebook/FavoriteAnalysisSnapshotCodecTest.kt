package com.permieware.osmapdigger.notebook

import com.permieware.osmapdigger.domain.PreferredDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class FavoriteAnalysisSnapshotCodecTest {
    @Test
    fun snapshotRoundTripsWithFrozenCriteriaAndContributions() {
        val snapshot = sampleSnapshot()

        val restored = FavoriteAnalysisSnapshotCodec.decode(FavoriteAnalysisSnapshotCodec.encode(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun unsupportedSnapshotVersionIsRejected() {
        val payload = FavoriteAnalysisSnapshotCodec.encode(sampleSnapshot()).replace("\"version\":1", "\"version\":99")

        assertFails { FavoriteAnalysisSnapshotCodec.decode(payload) }
    }

    private fun sampleSnapshot() = FavoriteAnalysisSnapshot(
        datasetId = "dataset",
        settlementId = "saved",
        settlementName = "Saved",
        capturedAtEpochMs = 123L,
        scoreValue = 82.5,
        coverage = 75.0,
        knownWeight = 6,
        totalWeight = 8,
        centerSettlementId = "center",
        centerSettlementName = "Center",
        radiusKm = 25.0,
        requiredCriteria = listOf(
            FavoriteRequiredCriterionSnapshot("landfill.distance_km", "Landfill", "km", 5.0, null),
        ),
        preferences = listOf(
            FavoritePreferenceSnapshot(
                metricId = "forest.distance_km",
                title = "Forest",
                unit = "km",
                direction = PreferredDirection.LOWER,
                targetValue = 1.0,
                limitValue = 10.0,
                weight = 8,
                rawValue = 2.0,
                quality = 0.9,
                weightedContribution = 7.2,
                scoreContribution = 90.0,
            ),
        ),
    )
}
