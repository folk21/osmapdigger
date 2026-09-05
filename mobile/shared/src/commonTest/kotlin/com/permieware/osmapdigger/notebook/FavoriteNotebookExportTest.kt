package com.permieware.osmapdigger.notebook

import com.permieware.osmapdigger.domain.PreferredDirection
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoriteNotebookExportTest {
    @Test
    fun exportIsDeterministicAndKeepsUnavailableFavorites() {
        val older = favorite("b", "Saved B", 100L, note = "Second")
        val newer = favorite("a", "Saved A", 200L, snapshot = snapshot("a", "Saved A"))

        val forward = FavoriteNotebookExportBuilder.build(
            datasetId = "belarus",
            favorites = listOf(older, newer),
            currentDisplayNames = mapOf("a" to "Локальное A"),
        )
        val reversed = FavoriteNotebookExportBuilder.build(
            datasetId = "belarus",
            favorites = listOf(newer, older),
            currentDisplayNames = mapOf("a" to "Локальное A"),
        )

        assertEquals(FavoriteNotebookExportBuilder.ARCHIVE_FILE_NAME, forward.fileName)
        assertEquals(
            listOf(FavoriteNotebookExportBuilder.MANIFEST_FILE_NAME, FavoriteNotebookExportBuilder.SUMMARY_FILE_NAME),
            forward.files.map { it.name },
        )
        assertEquals(forward.files.map { it.name }, reversed.files.map { it.name })
        forward.files.zip(reversed.files).forEach { (first, second) ->
            assertContentEquals(first.content, second.content)
        }

        val manifest = forward.files.first { it.name == FavoriteNotebookExportBuilder.MANIFEST_FILE_NAME }.content.decodeToString()
        val summary = forward.files.first { it.name == FavoriteNotebookExportBuilder.SUMMARY_FILE_NAME }.content.decodeToString()

        assertTrue(manifest.contains("\"format\": \"osmapdigger-favorites\""))
        assertTrue(manifest.contains("\"version\": 1"))
        assertTrue(manifest.indexOf("\"settlementId\": \"a\"") < manifest.indexOf("\"settlementId\": \"b\""))
        assertTrue(manifest.contains("\"currentDisplayName\": \"Локальное A\""))
        assertTrue(manifest.contains("\"currentDisplayName\": null"))
        assertTrue(manifest.contains("\"savedDisplayName\": \"Saved B\""))
        assertTrue(manifest.contains("\"savedScoreValue\": 81.5"))
        assertTrue(manifest.contains("\"savedCoverage\": 75.0"))
        assertTrue(manifest.contains("\"analysisSnapshot\""))
        assertTrue(manifest.contains("\"requiredCriteria\""))
        assertTrue(manifest.contains("\"preferences\""))
        assertTrue(summary.contains("## 1. Локальное A"))
        assertTrue(summary.contains("## 2. Saved B"))
        assertTrue(summary.contains("- Note: Second"))
        assertFalse(manifest.contains("/Users/"))
        assertFalse(manifest.contains("data/source/osm"))
    }

    private fun favorite(
        id: String,
        name: String,
        addedAt: Long,
        note: String? = null,
        snapshot: FavoriteAnalysisSnapshot? = null,
    ) = FavoriteSettlement(
        datasetId = "belarus",
        settlementId = id,
        settlementName = name,
        addedAtEpochMs = addedAt,
        note = note,
        analysisSnapshot = snapshot,
    )

    private fun snapshot(id: String, name: String) = FavoriteAnalysisSnapshot(
        datasetId = "belarus",
        settlementId = id,
        settlementName = name,
        capturedAtEpochMs = 300L,
        scoreValue = 81.5,
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
