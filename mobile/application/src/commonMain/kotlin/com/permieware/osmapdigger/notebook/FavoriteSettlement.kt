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
    val note: String? = null,
    val analysisSnapshot: FavoriteAnalysisSnapshot? = null,
) {
    init {
        require(datasetId.isNotBlank()) { "Favorite dataset ID must not be blank" }
        require(settlementId.isNotBlank()) { "Favorite settlement ID must not be blank" }
        require(settlementName.isNotBlank()) { "Favorite settlement name must not be blank" }
        require(addedAtEpochMs >= 0L) { "Favorite timestamp must not be negative" }
        require(note == null || note.isNotBlank()) { "Favorite note must be null or non-blank" }
    }
}

/** Application-owned persistence boundary for saved settlements. */
interface FavoriteSettlementRepository {
    suspend fun list(datasetId: String): List<FavoriteSettlement>

    suspend fun add(
        datasetId: String,
        settlement: Settlement,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft?,
    ): FavoriteSettlement

    suspend fun updateNote(
        datasetId: String,
        settlementId: String,
        note: String?,
    ): FavoriteSettlement

    suspend fun updateSnapshot(
        datasetId: String,
        settlementId: String,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft,
    ): FavoriteSettlement

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

    override suspend fun add(
        datasetId: String,
        settlement: Settlement,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft?,
    ): FavoriteSettlement =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not save favorite settlement") {
            delegate.add(datasetId, settlement, analysisSnapshot)
        }

    override suspend fun updateNote(
        datasetId: String,
        settlementId: String,
        note: String?,
    ): FavoriteSettlement =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not save favorite note") {
            delegate.updateNote(datasetId, settlementId, note)
        }

    override suspend fun updateSnapshot(
        datasetId: String,
        settlementId: String,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft,
    ): FavoriteSettlement =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not update favorite analysis snapshot") {
            delegate.updateSnapshot(datasetId, settlementId, analysisSnapshot)
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

    override suspend fun add(
        datasetId: String,
        settlement: Settlement,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft?,
    ): FavoriteSettlement =
        FavoriteSettlement(datasetId, settlement.id, settlement.name, 0L, analysisSnapshot = analysisSnapshot?.capturedAt(0L))

    override suspend fun updateNote(
        datasetId: String,
        settlementId: String,
        note: String?,
    ): FavoriteSettlement = error("Favorite is not available in the empty repository")

    override suspend fun updateSnapshot(
        datasetId: String,
        settlementId: String,
        analysisSnapshot: FavoriteAnalysisSnapshotDraft,
    ): FavoriteSettlement = error("Favorite is not available in the empty repository")

    override suspend fun remove(datasetId: String, settlementId: String) = Unit

    override suspend fun clear(datasetId: String) = Unit
}
