package com.permieware.osmapdigger.workspace

import com.permieware.osmapdigger.analysis.ScoredSettlement

/**
 * Transient stable-ID selection used for multi-settlement actions in the current analysis session.
 *
 * Shortlist membership is intentionally independent from the single map/details selection and from
 * persistence. When ordered results are needed, current ranked order remains authoritative rather
 * than introducing a second user-managed ordering model.
 */
data class SettlementShortlist(
    val settlementIds: Set<String> = emptySet(),
) {
    init {
        require(settlementIds.none { it.isBlank() }) { "Shortlist settlement IDs must not be blank" }
    }

    val size: Int
        get() = settlementIds.size

    fun contains(settlementId: String): Boolean = settlementId in settlementIds

    fun withSettlement(
        settlementId: String,
        included: Boolean,
    ): SettlementShortlist {
        require(settlementId.isNotBlank()) { "Shortlist settlement ID must not be blank" }
        val nextIds =
            if (included) {
                settlementIds + settlementId
            } else {
                settlementIds - settlementId
            }
        return if (nextIds == settlementIds) this else SettlementShortlist(nextIds)
    }

    fun clear(): SettlementShortlist = if (settlementIds.isEmpty()) this else SettlementShortlist()

    /** Return shortlisted settlements that are currently visible, preserving current ranked order. */
    fun orderedVisibleResults(rankedResults: List<ScoredSettlement>): List<ScoredSettlement> =
        rankedResults.filter { it.settlement.id in settlementIds }
}
