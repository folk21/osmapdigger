package com.permieware.osmapdigger.desktop.notebook

import com.permieware.osmapdigger.desktop.settings.DesktopSettingsDatabase
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.notebook.FavoriteAnalysisSnapshotCodec
import com.permieware.osmapdigger.notebook.FavoriteAnalysisSnapshotDraft
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.notebook.FavoriteSettlementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Connection

/** Desktop SQLite adapter for dataset-scoped favorite settlements and one frozen analysis snapshot. */
class SqliteFavoriteSettlementRepository(
    databasePath: Path,
) : FavoriteSettlementRepository {
    private val database = DesktopSettingsDatabase(databasePath)

    override suspend fun list(datasetId: String): List<FavoriteSettlement> =
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openConnection().use { connection -> list(connection, datasetId) }
        }

    override suspend fun add(
        datasetId: String,
        settlement: Settlement,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft?,
    ): FavoriteSettlement = withContext(Dispatchers.IO) {
        database.initialize()
        val addedAt = System.currentTimeMillis()
        database.openConnection().use { connection ->
            connection.autoCommit = false
            try {
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
                if (analysisSnapshot != null) {
                    upsertSnapshot(connection, datasetId, settlement.id, analysisSnapshot, addedAt)
                }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = true
            }
        }
        requireNotNull(list(datasetId).firstOrNull { it.settlementId == settlement.id })
    }

    override suspend fun updateNote(
        datasetId: String,
        settlementId: String,
        note: String?,
    ): FavoriteSettlement = withContext(Dispatchers.IO) {
        database.initialize()
        val normalized = note?.trim()?.takeIf { it.isNotEmpty() }
        database.openConnection().use { connection ->
            connection.prepareStatement(
                "UPDATE favorite_settlement SET note_text = ? WHERE dataset_id = ? AND settlement_id = ?",
            ).use { statement ->
                statement.setString(1, normalized)
                statement.setString(2, datasetId)
                statement.setString(3, settlementId)
                check(statement.executeUpdate() == 1) { "Favorite settlement is not available" }
            }
        }
        requireNotNull(list(datasetId).firstOrNull { it.settlementId == settlementId })
    }

    override suspend fun updateSnapshot(
        datasetId: String,
        settlementId: String,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft,
    ): FavoriteSettlement = withContext(Dispatchers.IO) {
        database.initialize()
        val capturedAt = System.currentTimeMillis()
        database.openConnection().use { connection ->
            checkFavoriteExists(connection, datasetId, settlementId)
            upsertSnapshot(connection, datasetId, settlementId, analysisSnapshot, capturedAt)
        }
        requireNotNull(list(datasetId).firstOrNull { it.settlementId == settlementId })
    }

    override suspend fun remove(datasetId: String, settlementId: String) {
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openConnection().use { connection ->
                connection.autoCommit = false
                try {
                    connection.prepareStatement(
                        "DELETE FROM favorite_analysis_snapshot WHERE dataset_id = ? AND settlement_id = ?",
                    ).use { statement ->
                        statement.setString(1, datasetId)
                        statement.setString(2, settlementId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        "DELETE FROM favorite_settlement WHERE dataset_id = ? AND settlement_id = ?",
                    ).use { statement ->
                        statement.setString(1, datasetId)
                        statement.setString(2, settlementId)
                        statement.executeUpdate()
                    }
                    connection.commit()
                } catch (failure: Throwable) {
                    connection.rollback()
                    throw failure
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }

    override suspend fun clear(datasetId: String) {
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openConnection().use { connection ->
                connection.autoCommit = false
                try {
                    connection.prepareStatement(
                        "DELETE FROM favorite_analysis_snapshot WHERE dataset_id = ?",
                    ).use { statement ->
                        statement.setString(1, datasetId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        "DELETE FROM favorite_settlement WHERE dataset_id = ?",
                    ).use { statement ->
                        statement.setString(1, datasetId)
                        statement.executeUpdate()
                    }
                    connection.commit()
                } catch (failure: Throwable) {
                    connection.rollback()
                    throw failure
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }

    private fun list(connection: Connection, datasetId: String): List<FavoriteSettlement> =
        connection.prepareStatement(
            """
            SELECT f.settlement_id, f.settlement_name, f.added_at_epoch_ms, f.note_text,
                   s.snapshot_json
            FROM favorite_settlement f
            LEFT JOIN favorite_analysis_snapshot s
              ON s.dataset_id = f.dataset_id AND s.settlement_id = f.settlement_id
            WHERE f.dataset_id = ?
            ORDER BY f.added_at_epoch_ms DESC, f.settlement_id ASC
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
                                note = result.getString("note_text")?.takeIf { it.isNotBlank() },
                                analysisSnapshot = result.getString("snapshot_json")?.let(FavoriteAnalysisSnapshotCodec::decode),
                            ),
                        )
                    }
                }
            }
        }

    private fun checkFavoriteExists(connection: Connection, datasetId: String, settlementId: String) {
        connection.prepareStatement(
            "SELECT 1 FROM favorite_settlement WHERE dataset_id = ? AND settlement_id = ?",
        ).use { statement ->
            statement.setString(1, datasetId)
            statement.setString(2, settlementId)
            statement.executeQuery().use { result ->
                check(result.next()) { "Favorite settlement is not available" }
            }
        }
    }

    private fun upsertSnapshot(
        connection: Connection,
        datasetId: String,
        settlementId: String,
        draft: FavoriteAnalysisSnapshotDraft,
        capturedAt: Long,
    ) {
        draft.requireIdentity(datasetId, settlementId)
        val snapshot = draft.capturedAt(capturedAt)
        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO favorite_analysis_snapshot(dataset_id, settlement_id, snapshot_json, captured_at_epoch_ms)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, datasetId)
            statement.setString(2, settlementId)
            statement.setString(3, FavoriteAnalysisSnapshotCodec.encode(snapshot))
            statement.setLong(4, capturedAt)
            statement.executeUpdate()
        }
    }

    companion object {
        fun createDefault(): SqliteFavoriteSettlementRepository =
            SqliteFavoriteSettlementRepository(DesktopSettingsDatabase.defaultPath())
    }
}
