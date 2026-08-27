package com.permieware.osmapdigger.settings

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Owns the Android application settings SQLite schema and migrations. */
class AndroidSettingsDatabase(
    val file: File,
) {
    fun initialize() {
        file.parentFile?.mkdirs()
        openDatabase().use { database ->
            when (database.version) {
                0 -> createSchema(database)
                1 -> migrateFrom1To2(database)
                SCHEMA_VERSION -> Unit
                else -> error(
                    "Unsupported settings database schema version ${database.version}; " +
                        "expected <= $SCHEMA_VERSION",
                )
            }
        }
    }

    fun openDatabase(): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(file, null)

    private fun createSchema(database: SQLiteDatabase) {
        database.execSQL(USER_PREFERENCES_SCHEMA)
        database.execSQL(EXTERNAL_SEARCH_PROVIDER_SCHEMA)
        database.version = SCHEMA_VERSION
    }

    private fun migrateFrom1To2(database: SQLiteDatabase) {
        database.execSQL(EXTERNAL_SEARCH_PROVIDER_SCHEMA)
        database.version = SCHEMA_VERSION
    }

    companion object {
        const val SCHEMA_VERSION = 2
        const val GLOBAL_COUNTRY_CODE = "*"

        private const val USER_PREFERENCES_SCHEMA = """
            CREATE TABLE user_preferences (
                id INTEGER PRIMARY KEY NOT NULL CHECK(id = 1),
                dataset_id TEXT NOT NULL,
                center_settlement_id TEXT,
                center_settlement_name TEXT,
                radius_km REAL,
                filters_json TEXT NOT NULL
            )
        """

        private const val EXTERNAL_SEARCH_PROVIDER_SCHEMA = """
            CREATE TABLE IF NOT EXISTS external_search_provider (
                provider_id TEXT NOT NULL,
                title TEXT NOT NULL,
                country_code TEXT NOT NULL,
                url_template TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0, 1)),
                priority INTEGER NOT NULL DEFAULT 100,
                PRIMARY KEY(provider_id, country_code)
            )
        """
    }
}
