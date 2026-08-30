package com.permieware.osmapdigger.desktop.preferences

import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalFailure

import com.permieware.osmapdigger.desktop.settings.DesktopSettingsDatabase
import com.permieware.osmapdigger.preferences.MetricPreferenceOverridePayloadCodec
import com.permieware.osmapdigger.preferences.SearchConditionPayloadCodec
import com.permieware.osmapdigger.preferences.SettlementCandidateScopePayloadCodec
import com.permieware.osmapdigger.preferences.UserPreferences
import com.permieware.osmapdigger.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Types

/** Desktop application-owned SQLite store for the current user search context. */
class SqliteUserPreferencesRepository(
    databasePath: Path,
) : UserPreferencesRepository {
    private val database = DesktopSettingsDatabase(databasePath)

    override suspend fun load(): UserPreferences? =
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openConnection().use { connection ->
                connection.prepareStatement(
                    """
                    SELECT dataset_id, center_settlement_id, center_settlement_name, radius_km, filters_json, preferences_json, candidate_scope_json
                    FROM user_preferences
                    WHERE id = 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        if (!result.next()) {
                            return@withContext null
                        }

                        val conditions = SearchConditionPayloadCodec.decode(result.getString("filters_json"))
                            ?: operationalFailure(OperationalFailureKind.SETTINGS, "Stored filter payload is invalid")
                        val preferenceOverrides =
                            MetricPreferenceOverridePayloadCodec.decode(result.getString("preferences_json"))
                                ?: operationalFailure(OperationalFailureKind.SETTINGS, "Stored preference payload is invalid")
                        val candidateScope =
                            SettlementCandidateScopePayloadCodec.decode(result.getString("candidate_scope_json"))
                                ?: operationalFailure(OperationalFailureKind.SETTINGS, "Stored candidate scope payload is invalid")
                        val radius = result.getDouble("radius_km").let { if (result.wasNull()) null else it }

                        UserPreferences(
                            datasetId = result.getString("dataset_id"),
                            centerSettlementId = result.getString("center_settlement_id"),
                            centerSettlementName = result.getString("center_settlement_name"),
                            radiusKm = radius,
                            conditions = conditions,
                            preferenceOverrides = preferenceOverrides,
                            candidateScope = candidateScope,
                        )
                    }
                }
            }
        }

    override suspend fun save(preferences: UserPreferences) {
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openConnection().use { connection ->
                connection.prepareStatement(
                    """
                    INSERT OR REPLACE INTO user_preferences(
                        id, dataset_id, center_settlement_id, center_settlement_name, radius_km, filters_json, preferences_json, candidate_scope_json
                    ) VALUES (1, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, preferences.datasetId)
                    statement.setString(2, preferences.centerSettlementId)
                    statement.setString(3, preferences.centerSettlementName)
                    val radiusKm = preferences.radiusKm
                    if (radiusKm == null) {
                        statement.setNull(4, Types.REAL)
                    } else {
                        statement.setDouble(4, radiusKm)
                    }
                    statement.setString(5, SearchConditionPayloadCodec.encode(preferences.conditions))
                    statement.setString(6, MetricPreferenceOverridePayloadCodec.encode(preferences.preferenceOverrides))
                    statement.setString(7, SettlementCandidateScopePayloadCodec.encode(preferences.candidateScope))
                    statement.executeUpdate()
                }
            }
        }
    }

    companion object {
        /** Default Desktop settings location, separate from installed dataset directories. */
        fun createDefault(): SqliteUserPreferencesRepository =
            SqliteUserPreferencesRepository(DesktopSettingsDatabase.defaultPath())
    }
}
