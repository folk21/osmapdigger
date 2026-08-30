package com.permieware.osmapdigger.preferences

import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalFailure

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
                    SELECT dataset_id, center_settlement_id, center_settlement_name, radius_km, filters_json, preferences_json, candidate_scope_json
                    FROM user_preferences
                    WHERE id = 1
                    """.trimIndent(),
                    emptyArray<String>(),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return@withContext null
                    }

                    val conditions = SearchConditionPayloadCodec.decode(cursor.getString(4))
                        ?: operationalFailure(OperationalFailureKind.SETTINGS, "Stored filter payload is invalid")
                    val preferenceOverrides = MetricPreferenceOverridePayloadCodec.decode(cursor.getString(5))
                        ?: operationalFailure(OperationalFailureKind.SETTINGS, "Stored preference payload is invalid")
                    val candidateSource = SettlementCandidateScopePayloadCodec.decode(cursor.getString(6))
                        ?: operationalFailure(OperationalFailureKind.SETTINGS, "Stored candidate scope payload is invalid")

                    UserPreferences(
                        datasetId = cursor.getString(0),
                        centerSettlementId = cursor.takeString(1),
                        centerSettlementName = cursor.takeString(2),
                        radiusKm = if (cursor.isNull(3)) null else cursor.getDouble(3),
                        conditions = conditions,
                        preferenceOverrides = preferenceOverrides,
                        candidateScope = candidateSource.candidateScope,
                        importedCandidateList = candidateSource.importedCandidateList,
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
                        id, dataset_id, center_settlement_id, center_settlement_name, radius_km, filters_json, preferences_json, candidate_scope_json
                    ) VALUES (1, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        preferences.datasetId,
                        preferences.centerSettlementId,
                        preferences.centerSettlementName,
                        preferences.radiusKm,
                        SearchConditionPayloadCodec.encode(preferences.conditions),
                        MetricPreferenceOverridePayloadCodec.encode(preferences.preferenceOverrides),
                        SettlementCandidateScopePayloadCodec.encode(
                            preferences.candidateScope,
                            preferences.importedCandidateList,
                        ),
                    ),
                )
            }
        }
    }

    private fun android.database.Cursor.takeString(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
