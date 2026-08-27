package com.permieware.osmapdigger.desktop.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager

/** Owns the Desktop application settings SQLite schema and migrations. */
class DesktopSettingsDatabase(
    val path: Path,
) {
    fun initialize() {
        Files.createDirectories(path.toAbsolutePath().parent)
        Class.forName("org.sqlite.JDBC")
        openConnection().use { connection ->
            when (val version = userVersion(connection)) {
                0 -> createSchema(connection)
                1 -> migrateFrom1To2(connection)
                SCHEMA_VERSION -> Unit
                else -> error(
                    "Unsupported settings database schema version $version; expected <= $SCHEMA_VERSION",
                )
            }
        }
    }

    fun openConnection(): Connection =
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")

    private fun createSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(USER_PREFERENCES_SCHEMA)
            statement.executeUpdate(EXTERNAL_SEARCH_PROVIDER_SCHEMA)
            statement.execute("PRAGMA user_version = $SCHEMA_VERSION")
        }
    }

    private fun migrateFrom1To2(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(EXTERNAL_SEARCH_PROVIDER_SCHEMA)
            statement.execute("PRAGMA user_version = $SCHEMA_VERSION")
        }
    }

    private fun userVersion(connection: Connection): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { result ->
                result.next()
                result.getInt(1)
            }
        }

    companion object {
        const val SCHEMA_VERSION = 2
        const val GLOBAL_COUNTRY_CODE = "*"

        private val USER_PREFERENCES_SCHEMA =
            """
            CREATE TABLE user_preferences (
                id INTEGER PRIMARY KEY NOT NULL CHECK(id = 1),
                dataset_id TEXT NOT NULL,
                center_settlement_id TEXT,
                center_settlement_name TEXT,
                radius_km REAL,
                filters_json TEXT NOT NULL
            )
            """.trimIndent()

        private val EXTERNAL_SEARCH_PROVIDER_SCHEMA =
            """
            CREATE TABLE IF NOT EXISTS external_search_provider (
                provider_id TEXT NOT NULL,
                title TEXT NOT NULL,
                country_code TEXT NOT NULL,
                url_template TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0, 1)),
                priority INTEGER NOT NULL DEFAULT 100,
                PRIMARY KEY(provider_id, country_code)
            )
            """.trimIndent()

        fun defaultPath(): Path =
            Paths.get(
                System.getProperty("user.home"),
                ".osmapdigger",
                "settings",
                "preferences.sqlite",
            )
    }
}
