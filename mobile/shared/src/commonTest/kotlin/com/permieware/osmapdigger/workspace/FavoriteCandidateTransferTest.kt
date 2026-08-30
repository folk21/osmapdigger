package com.permieware.osmapdigger.workspace

import com.permieware.osmapdigger.notebook.FavoriteSettlement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FavoriteCandidateTransferTest {
    @Test
    fun selectedFavoritesBecomeImportedCandidatesWithoutNameResolution() {
        val favorites =
            listOf(
                favorite("a", "Alpha", 30),
                favorite("b", "Beta", 20),
                favorite("c", "Gamma", 10),
            )

        val imported =
            FavoriteCandidateTransfer.build(
                favorites = favorites,
                selectedSettlementIds = setOf("c", "a"),
                availableSettlementIds = setOf("a", "b", "c"),
            )

        assertEquals(listOf("a", "c"), imported?.settlementIds)
        assertEquals("Alpha\nGamma", imported?.sourceText)
    }

    @Test
    fun unavailableFavoritesAreNotCopiedBackIntoAnalysis() {
        val favorites = listOf(favorite("old", "Old name", 20), favorite("live", "Live", 10))

        val imported =
            FavoriteCandidateTransfer.build(
                favorites = favorites,
                selectedSettlementIds = setOf("old", "live"),
                availableSettlementIds = setOf("live"),
            )

        assertEquals(listOf("live"), imported?.settlementIds)
        assertEquals("Live", imported?.sourceText)
    }

    @Test
    fun emptyOrUnavailableSelectionProducesNoImport() {
        val favorites = listOf(favorite("old", "Old name", 10))

        assertNull(FavoriteCandidateTransfer.build(favorites, emptySet(), setOf("old")))
        assertNull(FavoriteCandidateTransfer.build(favorites, setOf("old"), emptySet()))
    }

    private fun favorite(id: String, name: String, addedAt: Long) =
        FavoriteSettlement(
            datasetId = "dataset",
            settlementId = id,
            settlementName = name,
            addedAtEpochMs = addedAt,
        )
}
