package com.permieware.osmapdigger.notebook

import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary

/** One dataset-scoped settlement saved by the user for later research. */
data class FavoriteSettlement(
    val datasetId: String,
    val settlementId: String,
    val settlementName: String,
    val addedAtEpochMs: Long,
) {
    init {
        require(datasetId.isNotBlank()) { "Favorite dataset ID must not be blank" }
        require(settlementId.isNotBlank()) { "Favorite settlement ID must not be blank" }
        require(settlementName.isNotBlank()) { "Favorite settlement name must not be blank" }
        require(addedAtEpochMs >= 0L) { "Favorite timestamp must not be negative" }
    }
}

/** Application-owned persistence boundary for saved settlements. */
interface FavoriteSettlementRepository {
    suspend fun list(datasetId: String): List<FavoriteSettlement>

    suspend fun add(datasetId: String, settlement: Settlement): FavoriteSettlement

    suspend fun remove(datasetId: String, settlementId: String)

    suspend fun clear(datasetId: String)
}

/** Classify notebook storage failures without coupling shared code to a platform database API. */
class OperationalFavoriteSettlementRepository(
    private val delegate: FavoriteSettlementRepository,
) : FavoriteSettlementRepository {
    override suspend fun list(datasetId: String): List<FavoriteSettlement> =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not load favorite settlements") {
            delegate.list(datasetId)
        }

    override suspend fun add(datasetId: String, settlement: Settlement): FavoriteSettlement =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not save favorite settlement") {
            delegate.add(datasetId, settlement)
        }

    override suspend fun remove(datasetId: String, settlementId: String) {
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not remove favorite settlement") {
            delegate.remove(datasetId, settlementId)
        }
    }

    override suspend fun clear(datasetId: String) {
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not clear favorite settlements") {
            delegate.clear(datasetId)
        }
    }
}

/** Empty implementation used by focused controller tests that do not exercise notebook persistence. */
object EmptyFavoriteSettlementRepository : FavoriteSettlementRepository {
    override suspend fun list(datasetId: String): List<FavoriteSettlement> = emptyList()

    override suspend fun add(datasetId: String, settlement: Settlement): FavoriteSettlement =
        FavoriteSettlement(datasetId, settlement.id, settlement.name, 0L)

    override suspend fun remove(datasetId: String, settlementId: String) = Unit

    override suspend fun clear(datasetId: String) = Unit
}
