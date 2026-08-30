package com.permieware.osmapdigger.desktop.notebook

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.notebook.FavoriteAnalysisSnapshotDraft
import com.permieware.osmapdigger.notebook.FavoritePreferenceSnapshot
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqliteFavoriteSettlementRepositoryTest {
    @Test
    fun favoritesAreDatasetScopedPersistentAndIdempotent() = runTest {
        val database = Files.createTempDirectory("osmapdigger-favorites-").resolve("preferences.sqlite")
        try {
            val repository = SqliteFavoriteSettlementRepository(database)
            val first = settlement("node:1", "First")
            val second = settlement("node:2", "Second")

            repository.add("dataset-a", first, snapshotDraft(75.0))
            repository.add("dataset-a", second, null)
            repository.add("dataset-a", first.copy(name = "First renamed"), null)
            repository.add("dataset-b", first, null)
            repository.updateNote("dataset-a", "node:1", "  inspect road access  ")

            val reopened = SqliteFavoriteSettlementRepository(database)
            val datasetA = reopened.list("dataset-a")
            assertEquals(2, datasetA.size)
            assertEquals(setOf("node:1", "node:2"), datasetA.mapTo(hashSetOf()) { it.settlementId })
            val savedFirst = datasetA.first { it.settlementId == "node:1" }
            assertEquals("First renamed", savedFirst.settlementName)
            assertEquals("inspect road access", savedFirst.note)
            assertEquals(75.0, assertNotNull(savedFirst.analysisSnapshot).scoreValue)
            assertEquals(listOf("node:1"), reopened.list("dataset-b").map { it.settlementId })

            reopened.updateSnapshot("dataset-a", "node:1", snapshotDraft(91.0))
            assertEquals(91.0, assertNotNull(reopened.list("dataset-a").first { it.settlementId == "node:1" }.analysisSnapshot).scoreValue)

            reopened.remove("dataset-a", "node:1")
            assertEquals(listOf("node:2"), reopened.list("dataset-a").map { it.settlementId })

            reopened.clear("dataset-a")
            assertTrue(reopened.list("dataset-a").isEmpty())
            assertEquals(listOf("node:1"), reopened.list("dataset-b").map { it.settlementId })
        } finally {
            database.parent.toFile().deleteRecursively()
        }
    }


    @Test
    fun versionFiveFavoritesMigrateToNotesAndSnapshotTable() = runTest {
        val directory = Files.createTempDirectory("osmapdigger-favorites-v5-")
        val database = directory.resolve("preferences.sqlite")
        try {
            Class.forName("org.sqlite.JDBC")
            java.sql.DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        CREATE TABLE favorite_settlement (
                            dataset_id TEXT NOT NULL,
                            settlement_id TEXT NOT NULL,
                            settlement_name TEXT NOT NULL,
                            added_at_epoch_ms INTEGER NOT NULL,
                            PRIMARY KEY(dataset_id, settlement_id)
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        "INSERT INTO favorite_settlement(dataset_id, settlement_id, settlement_name, added_at_epoch_ms) VALUES ('dataset-a', 'node:1', 'Legacy', 1)",
                    )
                    statement.execute("PRAGMA user_version = 5")
                }
            }

            val repository = SqliteFavoriteSettlementRepository(database)
            val migrated = repository.list("dataset-a").single()
            assertEquals("Legacy", migrated.settlementName)
            assertEquals(null, migrated.note)
            assertEquals(null, migrated.analysisSnapshot)

            repository.updateNote("dataset-a", "node:1", "after migration")
            repository.updateSnapshot("dataset-a", "node:1", snapshotDraft(88.0))
            val enriched = repository.list("dataset-a").single()
            assertEquals("after migration", enriched.note)
            assertEquals(88.0, assertNotNull(enriched.analysisSnapshot).scoreValue)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun snapshotDraft(score: Double) = FavoriteAnalysisSnapshotDraft(
        datasetId = "dataset-a",
        settlementId = "node:1",
        settlementName = "First",
        scoreValue = score,
        coverage = 100.0,
        knownWeight = 8,
        totalWeight = 8,
        centerSettlementId = null,
        centerSettlementName = null,
        radiusKm = null,
        requiredCriteria = emptyList(),
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

    private fun settlement(id: String, name: String) =
        Settlement(
            id = id,
            name = name,
            localName = null,
            englishName = null,
            placeType = "village",
            population = null,
            location = GeoPoint(0.0, 0.0),
        )
}
