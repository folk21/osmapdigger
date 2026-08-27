package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.runtime.GeoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementSearchServiceTest {
    @Test
    fun findsBelarusianSettlementByRussianAndEnglishAliases() = runTest {
        val vitebsk = settlement("vitebsk", "Віцебск", 366299)
        val service =
            SettlementSearchService(
                FakeRepository(
                    listOf(
                        entry(vitebsk, "Віцебск", "Витебск", "Vitebsk"),
                    ),
                ),
            )

        assertEquals("vitebsk", service.find("Витебск").single().settlement.id)
        assertEquals("Витебск", service.find("Витебск").single().matchedName)
        assertEquals("vitebsk", service.find("Vitebsk").single().settlement.id)
    }

    @Test
    fun fuzzyLookupHandlesSmallTypo() = runTest {
        val vitebsk = settlement("vitebsk", "Віцебск", 366299)
        val service = SettlementSearchService(FakeRepository(listOf(entry(vitebsk, "Віцебск", "Витебск"))))

        val match = service.find("Витепск").single()

        assertEquals("vitebsk", match.settlement.id)
        assertEquals(SettlementMatchKind.FUZZY, match.kind)
        assertEquals("Витебск", match.matchedName)
    }

    @Test
    fun exactMatchRanksBeforeFuzzyAndPopulationBreaksEqualNameTies() = runTest {
        val small = settlement("small", "Дубровка", 20)
        val large = settlement("large", "Дубровка", 200)
        val typo = settlement("typo", "Дубровке", 500)
        val service =
            SettlementSearchService(
                FakeRepository(
                    listOf(
                        entry(small, "Дубровка"),
                        entry(large, "Дубровка"),
                        entry(typo, "Дубровке"),
                    ),
                ),
            )

        assertEquals(listOf("large", "small", "typo"), service.find("Дубровка").map { it.settlement.id })
    }

    @Test
    fun normalizerIsStableForCasePunctuationAndYo() {
        assertEquals("витебск район", SettlementNameNormalizer.normalize("  ВИТЁБСК, район! "))
    }

    private fun settlement(id: String, name: String, population: Long?) =
        Settlement(
            id = id,
            name = name,
            localName = name,
            englishName = null,
            placeType = "city",
            population = population,
            location = GeoPoint(0.0, 0.0),
        )

    private fun entry(settlement: Settlement, vararg names: String) =
        SettlementSearchEntry(
            settlement = settlement,
            names =
                names.mapIndexed { index, value ->
                    SettlementName(
                        value = value,
                        normalizedValue = SettlementNameNormalizer.normalize(value),
                        kind = if (index == 0) "primary" else "localized",
                    )
                },
        )

    private class FakeRepository(
        private val entries: List<SettlementSearchEntry>,
    ) : GeoRepository {
        override suspend fun datasetInfo(): DatasetInfo =
            DatasetInfo("test", "Test", null, GeoPoint(0.0, 0.0), 10.0, false, null, "property")

        override suspend fun metricDefinitions(): List<MetricDefinition> = emptyList()

        override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> = entries

        override suspend fun searchCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            limit: Int,
        ): List<Settlement> = emptyList()

        override suspend fun details(settlementId: String): SettlementDetails = error("not used")
    }
}
