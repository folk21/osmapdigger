package com.permieware.osmapdigger.workspace

import com.permieware.osmapdigger.analysis.ImportedCandidateList
import com.permieware.osmapdigger.notebook.FavoriteSettlement

/**
 * Build an imported candidate list from an explicit Favorites selection.
 *
 * Favorites remain persistent analysis output; this bridge is invoked only by an explicit user action.
 * Stable settlement IDs are transferred directly, while saved names are retained only as editable
 * source-text provenance for the imported-list editor.
 */
object FavoriteCandidateTransfer {
    fun build(
        favorites: List<FavoriteSettlement>,
        selectedSettlementIds: Set<String>,
        availableSettlementIds: Set<String>,
    ): ImportedCandidateList? {
        if (selectedSettlementIds.isEmpty()) return null

        val selected =
            favorites.filter { favorite ->
                favorite.settlementId in selectedSettlementIds &&
                    favorite.settlementId in availableSettlementIds
            }
        if (selected.isEmpty()) return null

        return ImportedCandidateList(
            sourceText = selected.joinToString("\n") { it.settlementName },
            settlementIds = selected.map { it.settlementId },
        )
    }
}
