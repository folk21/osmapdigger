package com.permieware.osmapdigger.preferences

import android.content.Context
import com.permieware.osmapdigger.settings.AndroidSettingsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android app-private SQLite store for the current user search context. */
class AndroidUserPreferencesRepository(
    databaseFile: File,
) : UserPreferencesRepository {
    private val database = AndroidSettingsDatabase(databaseFile)

    constructor(context: Context) : this(File(context.filesDir, "settings/preferences.sqlite"))

    override suspend fun load(): UserPreferences? =
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openDatabase().use { sqlite ->
                sqlite.rawQuery(
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
            database.initialize()
            database.openDatabase().use { sqlite ->
                sqlite.execSQL(
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

    private fun android.database.Cursor.takeString(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
