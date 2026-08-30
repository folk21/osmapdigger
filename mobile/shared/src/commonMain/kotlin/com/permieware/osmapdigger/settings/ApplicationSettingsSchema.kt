package com.permieware.osmapdigger.settings

/** One platform-independent semantic definition of the application-owned settings SQLite schema. */
data class SettingsMigration(
    val fromVersion: Int,
    val toVersion: Int,
    val statements: List<String>,
) {
    init {
        require(fromVersion >= 0)
        require(toVersion == fromVersion + 1)
        require(statements.isNotEmpty())
    }
}

/**
 * Logical settings schema and ordered migrations shared by Android and Desktop execution adapters.
 * Platform owners still control transactions, database APIs, paths, and resource lifecycle.
 */
object ApplicationSettingsSchema {
    const val VERSION: Int = 4
    const val GLOBAL_COUNTRY_CODE: String = "*"
    const val DEFAULT_CANDIDATE_SCOPE_PAYLOAD: String = "{\"version\":1,\"source\":\"dataset\",\"settlementIds\":[]}"

    val createStatements: List<String> =
        listOf(
            """
            CREATE TABLE user_preferences (
                id INTEGER PRIMARY KEY NOT NULL CHECK(id = 1),
                dataset_id TEXT NOT NULL,
                center_settlement_id TEXT,
                center_settlement_name TEXT,
                radius_km REAL,
                filters_json TEXT NOT NULL,
                preferences_json TEXT NOT NULL DEFAULT '{"version":1,"preferences":[]}',
                candidate_scope_json TEXT NOT NULL DEFAULT '$DEFAULT_CANDIDATE_SCOPE_PAYLOAD'
            )
            """.trimIndent(),
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
            """.trimIndent(),
        )

    val migrations: List<SettingsMigration> =
        listOf(
            SettingsMigration(
                fromVersion = 1,
                toVersion = 2,
                statements = listOf(createStatements[1]),
            ),
            SettingsMigration(
                fromVersion = 2,
                toVersion = 3,
                statements =
                    listOf(
                        """
                        ALTER TABLE user_preferences
                        ADD COLUMN preferences_json TEXT NOT NULL DEFAULT '{"version":1,"preferences":[]}'
                        """.trimIndent(),
                    ),
            ),
            SettingsMigration(
                fromVersion = 3,
                toVersion = 4,
                statements =
                    listOf(
                        """
                        ALTER TABLE user_preferences
                        ADD COLUMN candidate_scope_json TEXT NOT NULL DEFAULT '$DEFAULT_CANDIDATE_SCOPE_PAYLOAD'
                        """.trimIndent(),
                    ),
            ),
        )

    /** Return the deterministic migration path, or null when the stored version is unsupported. */
    fun migrationsFrom(version: Int): List<SettingsMigration>? {
        if (version == VERSION) return emptyList()
        if (version <= 0 || version > VERSION) return null

        val bySource = migrations.associateBy { it.fromVersion }
        val result = mutableListOf<SettingsMigration>()
        var current = version
        while (current < VERSION) {
            val step = bySource[current] ?: return null
            result += step
            current = step.toVersion
        }
        return result
    }
}
