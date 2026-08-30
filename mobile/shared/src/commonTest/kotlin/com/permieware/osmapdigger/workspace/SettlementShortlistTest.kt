package com.permieware.osmapdigger.workspace

import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.analysis.SettlementScore
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettlementShortlistTest {
    @Test
    fun membershipIsStableIdBasedAndIdempotent() {
        val empty = SettlementShortlist()
        val one = empty.withSettlement("b", included = true)
        val unchanged = one.withSettlement("b", included = true)
        val removed = one.withSettlement("b", included = false)

        assertTrue(one.contains("b"))
        assertSame(one, unchanged)
        assertFalse(removed.contains("b"))
        assertEquals(0, removed.size)
    }

    @Test
    fun visibleShortlistOrderAlwaysFollowsCurrentRanking() {
        val shortlist = SettlementShortlist(setOf("c", "a", "missing"))
        val ranked = listOf(scored("b"), scored("a"), scored("c"))

        assertEquals(
            listOf("a", "c"),
            shortlist.orderedVisibleResults(ranked).map { it.settlement.id },
        )
        assertEquals(3, shortlist.size)
    }

    private fun scored(id: String) =
        ScoredSettlement(
            settlement =
                Settlement(
                    id = id,
                    name = id,
                    localName = null,
                    englishName = null,
                    placeType = "village",
                    population = null,
                    location = GeoPoint(0.0, 0.0),
                ),
            score =
                SettlementScore(
                    value = 50.0,
                    coverage = 100.0,
                    knownWeight = 1,
                    totalWeight = 1,
                    contributions = emptyList(),
                ),
        )
}
