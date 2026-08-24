package com.permieware.osmapdigger.desktop.preferences

import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.preferences.UserPreferences
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqliteUserPreferencesRepositoryTest {
    @Test
    fun missingStateLoadsAsNull() = runTest {
        val database = Files.createTempDirectory("osmapdigger-preferences-").resolve("preferences.sqlite")
        try {
            val repository = SqliteUserPreferencesRepository(database)
            assertNull(repository.load())
        } finally {
            database.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun preferencesSurviveRepositoryRecreation() = runTest {
        val database = Files.createTempDirectory("osmapdigger-preferences-").resolve("preferences.sqlite")
        try {
            val expected =
                UserPreferences(
                    datasetId = "andorra",
                    centerSettlementId = "node:42",
                    centerSettlementName = "Ordino",
                    radiusKm = 35.5,
                    conditions =
                        listOf(
                            SearchCondition("forest.distance_km", maxValue = 2.0),
                            SearchCondition("farmyard.distance_km", minValue = 5.0),
                        ),
                )

            SqliteUserPreferencesRepository(database).save(expected)
            val reopened = SqliteUserPreferencesRepository(database)

            assertEquals(expected, reopened.load())
        } finally {
            database.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun malformedFilterPayloadMakesSavedStateUnavailable() = runTest {
        val database = Files.createTempDirectory("osmapdigger-preferences-").resolve("preferences.sqlite")
        try {
            val repository = SqliteUserPreferencesRepository(database)
            repository.save(UserPreferences(datasetId = "andorra"))

            DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        "UPDATE user_preferences SET filters_json = 'malformed' WHERE id = 1",
                    )
                }
            }

            assertNull(SqliteUserPreferencesRepository(database).load())
        } finally {
            database.parent.toFile().deleteRecursively()
        }
    }
}
