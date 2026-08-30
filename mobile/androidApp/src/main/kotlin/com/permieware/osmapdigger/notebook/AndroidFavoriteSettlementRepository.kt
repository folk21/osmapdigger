package com.permieware.osmapdigger.notebook

import android.content.Context
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.settings.AndroidSettingsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android app-private SQLite adapter for dataset-scoped favorite settlements. */
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
                    SELECT settlement_id, settlement_name, added_at_epoch_ms
                    FROM favorite_settlement
                    WHERE dataset_id = ?
                    ORDER BY added_at_epoch_ms DESC, settlement_id ASC
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
                                ),
                            )
                        }
                    }
                }
            }
        }

    override suspend fun add(datasetId: String, settlement: Settlement): FavoriteSettlement =
        withContext(Dispatchers.IO) {
            database.initialize()
            val addedAt = System.currentTimeMillis()
            database.openDatabase().use { sqlite ->
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
            }
            list(datasetId).first { it.settlementId == settlement.id }
        }

    override suspend fun remove(datasetId: String, settlementId: String) {
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openDatabase().use { sqlite ->
                sqlite.execSQL(
                    "DELETE FROM favorite_settlement WHERE dataset_id = ? AND settlement_id = ?",
                    arrayOf(datasetId, settlementId),
                )
            }
        }
    }

    override suspend fun clear(datasetId: String) {
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openDatabase().use { sqlite ->
                sqlite.execSQL(
                    "DELETE FROM favorite_settlement WHERE dataset_id = ?",
                    arrayOf(datasetId),
                )
            }
        }
    }
}
