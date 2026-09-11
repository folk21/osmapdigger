package com.permieware.osmapdigger.analysis

/** Stable ranking for settlements whose preference scores are already calculated. */
object SettlementRanker {
    /**
     * Sort by score descending, coverage descending, then stable settlement name and ID.
     * Settlements without an available score sort after scored settlements.
     */
    fun rank(settlements: List<ScoredSettlement>): List<ScoredSettlement> =
        settlements.sortedWith(
            compareByDescending<ScoredSettlement> { it.score.value }
                .thenByDescending { it.score.coverage }
                .thenBy { it.settlement.name }
                .thenBy { it.settlement.id },
        )
}
