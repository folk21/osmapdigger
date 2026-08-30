package com.permieware.osmapdigger.desktop.notebook

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqliteFavoriteSettlementRepositoryTest {
    @Test
    fun favoritesAreDatasetScopedPersistentAndIdempotent() = runTest {
        val database = Files.createTempDirectory("osmapdigger-favorites-").resolve("preferences.sqlite")
        try {
            val repository = SqliteFavoriteSettlementRepository(database)
            val first = settlement("node:1", "First")
            val second = settlement("node:2", "Second")

            repository.add("dataset-a", first)
            repository.add("dataset-a", second)
            repository.add("dataset-a", first.copy(name = "First renamed"))
            repository.add("dataset-b", first)

            val reopened = SqliteFavoriteSettlementRepository(database)
            val datasetA = reopened.list("dataset-a")
            assertEquals(2, datasetA.size)
            assertEquals(setOf("node:1", "node:2"), datasetA.mapTo(hashSetOf()) { it.settlementId })
            assertEquals("First renamed", datasetA.first { it.settlementId == "node:1" }.settlementName)
            assertEquals(listOf("node:1"), reopened.list("dataset-b").map { it.settlementId })

            reopened.remove("dataset-a", "node:1")
            assertEquals(listOf("node:2"), reopened.list("dataset-a").map { it.settlementId })

            reopened.clear("dataset-a")
            assertTrue(reopened.list("dataset-a").isEmpty())
            assertEquals(listOf("node:1"), reopened.list("dataset-b").map { it.settlementId })
        } finally {
            database.parent.toFile().deleteRecursively()
        }
    }

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
