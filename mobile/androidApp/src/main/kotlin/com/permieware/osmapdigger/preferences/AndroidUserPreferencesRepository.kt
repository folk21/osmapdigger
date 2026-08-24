package com.permieware.osmapdigger.preferences

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android app-private SQLite store for the current user search context. */
class AndroidUserPreferencesRepository(
    private val databaseFile: File,
) : UserPreferencesRepository {
    constructor(context: Context) : this(File(context.filesDir, "settings/preferences.sqlite"))

    override suspend fun load(): UserPreferences? =
        withContext(Dispatchers.IO) {
            initialize()
            openDatabase().use { database ->
                database.rawQuery(
                    """
                    SELECT dataset_id, center_settlement_id, center_settlement_name, radius_km, filters_json
                    FROM user_preferences
                    WHERE id = 1
                    """.trimIndent(),
                    emptyArray<String>(),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return@withContext null
                    }

                    val conditions = SearchConditionPayloadCodec.decode(cursor.getString(4))
                        ?: return@withContext null

                    UserPreferences(
                        datasetId = cursor.getString(0),
                        centerSettlementId = cursor.takeString(1),
                        centerSettlementName = cursor.takeString(2),
                        radiusKm = if (cursor.isNull(3)) null else cursor.getDouble(3),
                        conditions = conditions,
                    )
                }
            }
        }

    override suspend fun save(preferences: UserPreferences) {
        withContext(Dispatchers.IO) {
            initialize()
            openDatabase().use { database ->
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO user_preferences(
                        id, dataset_id, center_settlement_id, center_settlement_name, radius_km, filters_json
                    ) VALUES (1, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        preferences.datasetId,
                        preferences.centerSettlementId,
                        preferences.centerSettlementName,
                        preferences.radiusKm,
                        SearchConditionPayloadCodec.encode(preferences.conditions),
                    ),
                )
            }
        }
    }

    private fun initialize() {
        databaseFile.parentFile?.mkdirs()
        openDatabase().use { database ->
            when (database.version) {
                0 -> {
                    database.execSQL(
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
                    database.version = SCHEMA_VERSION
                }
                SCHEMA_VERSION -> Unit
                else -> error(
                    "Unsupported preferences database schema version ${database.version}; " +
                        "expected $SCHEMA_VERSION",
                )
            }
        }
    }

    private fun openDatabase(): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null)

    private fun android.database.Cursor.takeString(index: Int): String? =
        if (isNull(index)) null else getString(index)

    companion object {
        private const val SCHEMA_VERSION = 1
    }
}
