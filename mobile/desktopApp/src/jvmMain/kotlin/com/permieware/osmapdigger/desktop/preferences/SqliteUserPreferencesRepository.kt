package com.permieware.osmapdigger.desktop.preferences

import com.permieware.osmapdigger.preferences.SearchConditionPayloadCodec
import com.permieware.osmapdigger.preferences.UserPreferences
import com.permieware.osmapdigger.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager

/** Desktop application-owned SQLite store for the current user search context. */
class SqliteUserPreferencesRepository(
    private val databasePath: Path,
) : UserPreferencesRepository {
    override suspend fun load(): UserPreferences? =
        withContext(Dispatchers.IO) {
            initialize()
            openConnection().use { connection ->
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
            initialize()
            openConnection().use { connection ->
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
                        statement.setNull(4, java.sql.Types.REAL)
                    } else {
                        statement.setDouble(4, radiusKm)
                    }
                    statement.setString(5, SearchConditionPayloadCodec.encode(preferences.conditions))
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun initialize() {
        Files.createDirectories(databasePath.toAbsolutePath().parent)
        Class.forName("org.sqlite.JDBC")

        openConnection().use { connection ->
            val version = connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result ->
                    result.next()
                    result.getInt(1)
                }
            }

            when (version) {
                0 -> createSchema(connection)
                SCHEMA_VERSION -> Unit
                else -> error(
                    "Unsupported preferences database schema version $version; expected $SCHEMA_VERSION",
                )
            }
        }
    }

    private fun createSchema(connection: Connection) {
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
            statement.execute("PRAGMA user_version = $SCHEMA_VERSION")
        }
    }

    private fun openConnection(): Connection =
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}")

    companion object {
        private const val SCHEMA_VERSION = 1

        /** Default Desktop settings location, separate from installed dataset directories. */
        fun createDefault(): SqliteUserPreferencesRepository =
            SqliteUserPreferencesRepository(
                Paths.get(
                    System.getProperty("user.home"),
                    ".osmapdigger",
                    "settings",
                    "preferences.sqlite",
                ),
            )
    }
}
