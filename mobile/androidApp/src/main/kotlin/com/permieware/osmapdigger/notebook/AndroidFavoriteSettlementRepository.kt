package com.permieware.osmapdigger.notebook

import android.content.Context
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.settings.AndroidSettingsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android app-private SQLite adapter for dataset-scoped favorites, notes, and one frozen snapshot. */
class AndroidFavoriteSettlementRepository(
    databaseFile: File,
) : FavoriteSettlementRepository {
    private val database = AndroidSettingsDatabase(databaseFile)

    constructor(context: Context) : this(File(context.filesDir, "settings/preferences.sqlite"))

    override suspend fun list(datasetId: String): List<FavoriteSettlement> =
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openDatabase().use { sqlite ->
                sqlite.rawQuery(
                    """
                    SELECT f.settlement_id, f.settlement_name, f.added_at_epoch_ms, f.note_text,
                           s.snapshot_json
                    FROM favorite_settlement f
                    LEFT JOIN favorite_analysis_snapshot s
                      ON s.dataset_id = f.dataset_id AND s.settlement_id = f.settlement_id
                    WHERE f.dataset_id = ?
                    ORDER BY f.added_at_epoch_ms DESC, f.settlement_id ASC
                    """.trimIndent(),
                    arrayOf(datasetId),
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                FavoriteSettlement(
                                    datasetId = datasetId,
                                    settlementId = cursor.getString(0),
                                    settlementName = cursor.getString(1),
                                    addedAtEpochMs = cursor.getLong(2),
                                    note = cursor.getString(3)?.takeIf { it.isNotBlank() },
                                    analysisSnapshot = cursor.getString(4)?.let(FavoriteAnalysisSnapshotCodec::decode),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override suspend fun add(
        datasetId: String,
        settlement: Settlement,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft?,
    ): FavoriteSettlement = withContext(Dispatchers.IO) {
        database.initialize()
        val addedAt = System.currentTimeMillis()
        database.openDatabase().use { sqlite ->
            sqlite.beginTransaction()
            try {
                sqlite.execSQL(
                    """
                    INSERT OR IGNORE INTO favorite_settlement(dataset_id, settlement_id, settlement_name, added_at_epoch_ms)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(datasetId, settlement.id, settlement.name, addedAt),
                )
                sqlite.execSQL(
                    """
                    UPDATE favorite_settlement
                    SET settlement_name = ?
                    WHERE dataset_id = ? AND settlement_id = ?
                    """.trimIndent(),
                    arrayOf(settlement.name, datasetId, settlement.id),
                )
                analysisSnapshot?.let { draft ->
                    upsertSnapshot(sqlite, datasetId, settlement.id, draft, addedAt)
                }
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
        }
        list(datasetId).first { it.settlementId == settlement.id }
    }

    override suspend fun updateNote(
        datasetId: String,
        settlementId: String,
        note: String?,
    ): FavoriteSettlement = withContext(Dispatchers.IO) {
        database.initialize()
        val normalized = note?.trim()?.takeIf { it.isNotEmpty() }
        database.openDatabase().use { sqlite ->
            sqlite.execSQL(
                "UPDATE favorite_settlement SET note_text = ? WHERE dataset_id = ? AND settlement_id = ?",
                arrayOf<Any?>(normalized, datasetId, settlementId),
            )
        }
        list(datasetId).first { it.settlementId == settlementId }
    }

    override suspend fun updateSnapshot(
        datasetId: String,
        settlementId: String,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft,
    ): FavoriteSettlement = withContext(Dispatchers.IO) {
        database.initialize()
        val capturedAt = System.currentTimeMillis()
        database.openDatabase().use { sqlite ->
            sqlite.rawQuery(
                "SELECT 1 FROM favorite_settlement WHERE dataset_id = ? AND settlement_id = ?",
                arrayOf(datasetId, settlementId),
            ).use { cursor ->
                check(cursor.moveToNext()) { "Favorite settlement is not available" }
            }
            upsertSnapshot(sqlite, datasetId, settlementId, analysisSnapshot, capturedAt)
        }
        list(datasetId).first { it.settlementId == settlementId }
    }

    override suspend fun remove(datasetId: String, settlementId: String) {
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openDatabase().use { sqlite ->
                sqlite.beginTransaction()
                try {
                    sqlite.execSQL(
                        "DELETE FROM favorite_analysis_snapshot WHERE dataset_id = ? AND settlement_id = ?",
                        arrayOf(datasetId, settlementId),
                    )
                    sqlite.execSQL(
                        "DELETE FROM favorite_settlement WHERE dataset_id = ? AND settlement_id = ?",
                        arrayOf(datasetId, settlementId),
                    )
                    sqlite.setTransactionSuccessful()
                } finally {
                    sqlite.endTransaction()
                }
            }
        }
    }

    override suspend fun clear(datasetId: String) {
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openDatabase().use { sqlite ->
                sqlite.beginTransaction()
                try {
                    sqlite.execSQL(
                        "DELETE FROM favorite_analysis_snapshot WHERE dataset_id = ?",
                        arrayOf(datasetId),
                    )
                    sqlite.execSQL(
                        "DELETE FROM favorite_settlement WHERE dataset_id = ?",
                        arrayOf(datasetId),
                    )
                    sqlite.setTransactionSuccessful()
                } finally {
                    sqlite.endTransaction()
                }
            }
        }
    }

    private fun upsertSnapshot(
        sqlite: android.database.sqlite.SQLiteDatabase,
        datasetId: String,
        settlementId: String,
        draft: FavoriteAnalysisSnapshotDraft,
        capturedAt: Long,
    ) {
        draft.requireIdentity(datasetId, settlementId)
        val snapshot = draft.capturedAt(capturedAt)
        sqlite.execSQL(
            """
            INSERT OR REPLACE INTO favorite_analysis_snapshot(dataset_id, settlement_id, snapshot_json, captured_at_epoch_ms)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                datasetId,
                settlementId,
                FavoriteAnalysisSnapshotCodec.encode(snapshot),
                capturedAt,
            ),
        )
    }
}
