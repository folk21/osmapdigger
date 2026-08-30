package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.dataset.GeoRepository
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricPreferenceDefault
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementAnalysisCandidate
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.domain.SettlementMatchKind
import com.permieware.osmapdigger.domain.SettlementName
import com.permieware.osmapdigger.domain.SettlementSearchEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettlementListImportTest {
    @Test
    fun parserTrimsDropsBlanksAndCollapsesNormalizedDuplicatesInFirstOccurrenceOrder() {
        val parsed =
            SettlementListImportParser.parse(
                """
                  ВИТЁБСК

                Полоцк
                витебск
                  Орша
                """.trimIndent(),
            )

        assertEquals(listOf("ВИТЁБСК", "Полоцк", "Орша"), parsed.map { it.sourceText })
        assertEquals(listOf("витебск", "полоцк", "орша"), parsed.map { it.normalizedText })
    }

    @Test
    fun resolverAcceptsOnlyUniqueExactAliasesAndKeepsAmbiguousAndFuzzyRowsForReview() = runTest {
        val vitebsk = settlement("vitebsk", "Віцебск", population = 300_000)
        val dubrovkaLarge = settlement("dubrovka-large", "Дубровка", population = 200)
        val dubrovkaSmall = settlement("dubrovka-small", "Дубровка", population = 20)
        val repository =
            FakeRepository(
                listOf(
                    entry(vitebsk, "Віцебск", "Витебск", "Vitebsk"),
                    entry(dubrovkaSmall, "Дубровка"),
                    entry(dubrovkaLarge, "Дубровка"),
                ),
            )
        val lines = SettlementListImportParser.parse("Витебск\nVitebsk\nДубровка\nВитепск\nUnknown")

        val review = SettlementListImportResolver(repository).resolve(lines)

        val resolved = assertIs<SettlementImportResolution.Resolved>(review.resolutions[0])
        assertEquals("vitebsk", resolved.match.settlement.id)
        assertEquals(SettlementMatchKind.EXACT, resolved.match.kind)

        val resolvedAlias = assertIs<SettlementImportResolution.Resolved>(review.resolutions[1])
        assertEquals("vitebsk", resolvedAlias.match.settlement.id)

        val ambiguous = assertIs<SettlementImportResolution.Ambiguous>(review.resolutions[2])
        assertEquals(listOf("dubrovka-large", "dubrovka-small"), ambiguous.matches.map { it.settlement.id })
        assertTrue(ambiguous.matches.all { it.kind == SettlementMatchKind.EXACT })

        val fuzzy = assertIs<SettlementImportResolution.Unresolved>(review.resolutions[3])
        assertEquals("vitebsk", fuzzy.suggestions.first().settlement.id)
        assertEquals(SettlementMatchKind.FUZZY, fuzzy.suggestions.first().kind)

        val unknown = assertIs<SettlementImportResolution.Unresolved>(review.resolutions[4])
        assertTrue(unknown.suggestions.isEmpty())
        assertEquals(listOf("vitebsk"), review.resolvedSettlementIds)
        assertEquals(1, repository.searchIndexReads)
    }


    @Test
    fun reviewerAcceptsOnlyExplicitReviewedChoicesAndPreservesStableImportOrder() = runTest {
        val alpha = settlement("alpha", "Alpha", population = 100)
        val betaA = settlement("beta-a", "Beta", population = 80)
        val betaB = settlement("beta-b", "Beta", population = 40)
        val repository =
            FakeRepository(
                listOf(
                    entry(alpha, "Alpha"),
                    entry(betaA, "Beta"),
                    entry(betaB, "Beta"),
                ),
            )
        val review =
            SettlementListImportResolver(repository).resolve(
                SettlementListImportParser.parse("Alpha\nBeta\nAlph"),
            )

        val reviewed =
            SettlementImportReviewer.reviewedSettlementIds(
                review,
                mapOf(
                    "beta" to "beta-b",
                    "alph" to "alpha",
                ),
            )

        assertEquals(listOf("alpha", "beta-b"), reviewed)
        assertFailsWith<IllegalArgumentException> {
            SettlementImportReviewer.reviewedSettlementIds(
                review,
                mapOf("beta" to "not-a-reviewed-match"),
            )
        }
    }

    private fun settlement(id: String, name: String, population: Long?) =
        Settlement(
            id = id,
            name = name,
            localName = name,
            englishName = null,
            placeType = "village",
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
        var searchIndexReads: Int = 0

        override suspend fun datasetInfo(): DatasetInfo =
            DatasetInfo("test", "Test", null, GeoPoint(0.0, 0.0), 10.0, false, null, "property")

        override suspend fun metricDefinitions(): List<MetricDefinition> = emptyList()

        override suspend fun preferenceDefaults(): List<MetricPreferenceDefault> = emptyList()

        override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> {
            searchIndexReads += 1
            return entries
        }

        override suspend fun searchCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            limit: Int,
        ): List<Settlement> = emptyList()

        override suspend fun analysisCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            scoringMetricIds: Set<String>,
            candidateSettlementIds: Set<String>?,
        ): List<SettlementAnalysisCandidate> = emptyList()

        override suspend fun details(settlementId: String): SettlementDetails = error("not used")
    }
}
