package com.permieware.osmapdigger.desktop.notebook

import com.permieware.osmapdigger.desktop.settings.DesktopSettingsDatabase
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.notebook.FavoriteSettlementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/** Desktop SQLite adapter for dataset-scoped favorite settlements. */
class SqliteFavoriteSettlementRepository(
    databasePath: Path,
) : FavoriteSettlementRepository {
    private val database = DesktopSettingsDatabase(databasePath)

    override suspend fun list(datasetId: String): List<FavoriteSettlement> =
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openConnection().use { connection ->
                connection.prepareStatement(
                    """
                    SELECT settlement_id, settlement_name, added_at_epoch_ms
                    FROM favorite_settlement
                    WHERE dataset_id = ?
                    ORDER BY added_at_epoch_ms DESC, settlement_id ASC
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, datasetId)
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                add(
                                    FavoriteSettlement(
                                        datasetId = datasetId,
                                        settlementId = result.getString("settlement_id"),
                                        settlementName = result.getString("settlement_name"),
                                        addedAtEpochMs = result.getLong("added_at_epoch_ms"),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

    override suspend fun add(datasetId: String, settlement: Settlement): FavoriteSettlement =
        withContext(Dispatchers.IO) {
            database.initialize()
            val addedAt = System.currentTimeMillis()
            database.openConnection().use { connection ->
                connection.prepareStatement(
                    """
                    INSERT OR IGNORE INTO favorite_settlement(dataset_id, settlement_id, settlement_name, added_at_epoch_ms)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, datasetId)
                    statement.setString(2, settlement.id)
                    statement.setString(3, settlement.name)
                    statement.setLong(4, addedAt)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE favorite_settlement
                    SET settlement_name = ?
                    WHERE dataset_id = ? AND settlement_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, settlement.name)
                    statement.setString(2, datasetId)
                    statement.setString(3, settlement.id)
                    statement.executeUpdate()
                }
            }
            list(datasetId).first { it.settlementId == settlement.id }
        }

    override suspend fun remove(datasetId: String, settlementId: String) {
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openConnection().use { connection ->
                connection.prepareStatement(
                    "DELETE FROM favorite_settlement WHERE dataset_id = ? AND settlement_id = ?",
                ).use { statement ->
                    statement.setString(1, datasetId)
                    statement.setString(2, settlementId)
                    statement.executeUpdate()
                }
            }
        }
    }

    override suspend fun clear(datasetId: String) {
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openConnection().use { connection ->
                connection.prepareStatement(
                    "DELETE FROM favorite_settlement WHERE dataset_id = ?",
                ).use { statement ->
                    statement.setString(1, datasetId)
                    statement.executeUpdate()
                }
            }
        }
    }

    companion object {
        fun createDefault(): SqliteFavoriteSettlementRepository =
            SqliteFavoriteSettlementRepository(DesktopSettingsDatabase.defaultPath())
    }
}
