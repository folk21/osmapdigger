package com.permieware.osmapdigger.desktop.preferences

import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.preferences.MetricPreferenceOverride
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
                    preferenceOverrides =
                        listOf(
                            MetricPreferenceOverride(
                                metricId = "forest.distance_km",
                                enabled = true,
                                targetValue = 0.8,
                                limitValue = 8.0,
                                weight = 9,
                            ),
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
    fun schemaVersion2MigratesWithEmptyPreferenceOverrides() = runTest {
        val database = Files.createTempDirectory("osmapdigger-preferences-").resolve("preferences.sqlite")
        try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        CREATE TABLE user_preferences (
                            id INTEGER PRIMARY KEY NOT NULL CHECK(id = 1),
                            dataset_id TEXT NOT NULL,
                            center_settlement_id TEXT,
                            center_settlement_name TEXT,
                            radius_km REAL,
                            filters_json TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE TABLE external_search_provider (
                            provider_id TEXT NOT NULL,
                            title TEXT NOT NULL,
                            country_code TEXT NOT NULL,
                            url_template TEXT NOT NULL,
                            enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0, 1)),
                            priority INTEGER NOT NULL DEFAULT 100,
                            PRIMARY KEY(provider_id, country_code)
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        "INSERT INTO user_preferences(id, dataset_id, filters_json) " +
                            "VALUES (1, 'andorra', '{\"version\":1,\"filters\":[]}')",
                    )
                    statement.execute("PRAGMA user_version = 2")
                }
            }

            val loaded = SqliteUserPreferencesRepository(database).load()

            assertEquals("andorra", loaded?.datasetId)
            assertEquals(emptyList(), loaded?.preferenceOverrides)
            DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { connection ->
                val version = connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA user_version").use { result ->
                        result.next()
                        result.getInt(1)
                    }
                }
                assertEquals(3, version)
            }
        } finally {
            database.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun malformedPreferencePayloadMakesSavedStateUnavailable() = runTest {
        val database = Files.createTempDirectory("osmapdigger-preferences-").resolve("preferences.sqlite")
        try {
            val repository = SqliteUserPreferencesRepository(database)
            repository.save(UserPreferences(datasetId = "andorra"))

            DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        "UPDATE user_preferences SET preferences_json = 'malformed' WHERE id = 1",
                    )
                }
            }

            assertNull(SqliteUserPreferencesRepository(database).load())
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
