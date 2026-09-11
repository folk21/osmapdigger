package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementRankerTest {
    @Test
    fun rankingUsesScoreThenCoverageThenStableSettlementIdentity() {
        val ranked =
            SettlementRanker.rank(
                listOf(
                    scored("z", "Zulu", score = null, coverage = 0.0),
                    scored("b", "Beta", score = 90.0, coverage = 100.0),
                    scored("a2", "Alpha", score = 90.0, coverage = 100.0),
                    scored("a1", "Alpha", score = 90.0, coverage = 100.0),
                    scored("coverage", "Coverage", score = 90.0, coverage = 80.0),
                    scored("best", "Best", score = 95.0, coverage = 50.0),
                ),
            )

        assertEquals(
            listOf("best", "a1", "a2", "b", "coverage", "z"),
            ranked.map { it.settlement.id },
        )
    }

    private fun scored(
        id: String,
        name: String,
        score: Double?,
        coverage: Double,
    ) =
        ScoredSettlement(
            settlement =
                Settlement(
                    id = id,
                    name = name,
                    localName = null,
                    englishName = null,
                    placeType = "village",
                    population = null,
                    location = GeoPoint(0.0, 0.0),
                ),
            score =
                SettlementScore(
                    value = score,
                    coverage = coverage,
                    knownWeight = if (score == null) 0 else 1,
                    totalWeight = 1,
                    contributions = emptyList(),
                ),
        )
}
