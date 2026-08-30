package com.permieware.osmapdigger.analysis

/** Candidate universe used before hard constraints, radius filtering, and preference scoring. */
sealed interface SettlementCandidateScope {
    /** Analyze the normal dataset-wide candidate universe. */
    data object Dataset : SettlementCandidateScope

    /** Analyze only this reviewed, ordered set of stable settlement IDs. */
    data class Imported(
        val settlementIds: List<String>,
    ) : SettlementCandidateScope {
        init {
            require(settlementIds.none { it.isBlank() }) {
                "Imported candidate settlement IDs must not be blank"
            }
            require(settlementIds.distinct().size == settlementIds.size) {
                "Imported candidate settlement IDs must be unique"
            }
        }
    }
}

/** Return null for the unrestricted dataset scope and a stable ID set for an imported scope. */
internal fun SettlementCandidateScope.restrictedSettlementIds(): Set<String>? =
    when (this) {
        SettlementCandidateScope.Dataset -> null
        is SettlementCandidateScope.Imported -> settlementIds.toCollection(linkedSetOf())
    }
