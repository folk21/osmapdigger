package com.permieware.osmapdigger.desktop.preferences

import com.permieware.osmapdigger.desktop.settings.DesktopSettingsDatabase
import com.permieware.osmapdigger.preferences.SearchConditionPayloadCodec
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
                    SELECT dataset_id, center_settlement_id, center_settlement_name, radius_km, filters_json
                    FROM user_preferences
                    WHERE id = 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        if (!result.next()) {
                            return@withContext null
                        }

                        val conditions = SearchConditionPayloadCodec.decode(result.getString("filters_json"))
                            ?: return@withContext null
                        val radius = result.getDouble("radius_km").let { if (result.wasNull()) null else it }

                        UserPreferences(
                            datasetId = result.getString("dataset_id"),
                            centerSettlementId = result.getString("center_settlement_id"),
                            centerSettlementName = result.getString("center_settlement_name"),
                            radiusKm = radius,
                            conditions = conditions,
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
                        id, dataset_id, center_settlement_id, center_settlement_name, radius_km, filters_json
                    ) VALUES (1, ?, ?, ?, ?, ?)
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
