package com.permieware.osmapdigger.external

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalSearchProvidersTest {
    private val settlement =
        Settlement(
            id = "1",
            name = "Test Village",
            localName = null,
            englishName = null,
            placeType = "village",
            population = null,
            location = GeoPoint(0.0, 0.0),
        )

    @Test
    fun googleTemplateEncodesSettlementAndTerms() {
        val google = ExternalSearchProvider(
            id = "google",
            title = "Google",
            countryCode = null,
            urlTemplate = "https://www.google.com/search?q={query}",
            priority = 10,
        )
        val url = ExternalSearchUrlBuilder.build(google, settlement, terms = "house property")

        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("%22Test%20Village%22%20house%20property"))
    }

    @Test
    fun countrySpecificSeedUsesSiteRestrictedSearch() {
        val kufar = ExternalSearchProvider(
            id = "kufar-by",
            title = "Kufar",
            countryCode = "BY",
            urlTemplate = "https://www.google.com/search?q=site%3Are.kufar.by%20{query}",
            priority = 100,
        )
        val url = ExternalSearchUrlBuilder.build(kufar, settlement, terms = "house")

        assertEquals("BY", kufar.countryCode)
        assertTrue(url.contains("site%3Are.kufar.by"))
        assertTrue(url.contains("%22Test%20Village%22%20house"))
    }

    @Test
    fun batchSearchUsesQuotedCyrillicAlternativesAndSiteRestriction() {
        val kufar = ExternalSearchProvider(
            id = "kufar-by",
            title = "Kufar",
            countryCode = "BY",
            urlTemplate = "https://www.google.com/search?q=site%3Are.kufar.by%20{query}",
            priority = 100,
        )

        val actions =
            ExternalSearchBatchBuilder.build(
                providers = listOf(kufar),
                settlementNames = listOf("Нарочь", "Браслав"),
                terms = "дом недвижимость",
            )

        assertEquals(1, actions.size)
        val action = actions.single()
        assertEquals(listOf("Нарочь", "Браслав"), action.settlementNames)
        assertTrue(action.url.contains("site%3Are.kufar.by"))
        assertTrue(action.url.contains(percentEncode("(\"Нарочь\" OR \"Браслав\") дом недвижимость")))
    }

    @Test
    fun batchSearchSplitsDeterministicallyWithoutExceedingUrlLimit() {
        val provider = ExternalSearchProvider(
            id = "google",
            title = "Google",
            countryCode = null,
            urlTemplate = "https://www.google.com/search?q={query}",
            priority = 10,
        )
        val names = listOf("Alpha Village", "Beta Village", "Gamma Village", "Delta Village")
        val maxUrlLength = 105

        val first = ExternalSearchBatchBuilder.build(listOf(provider), names, terms = "house", maxUrlLength = maxUrlLength)
        val second = ExternalSearchBatchBuilder.build(listOf(provider), names, terms = "house", maxUrlLength = maxUrlLength)

        assertTrue(first.size > 1)
        assertEquals(first, second)
        assertEquals(names, first.flatMap { it.settlementNames })
        assertTrue(first.all { it.url.length <= maxUrlLength })
        assertEquals((1..first.size).toList(), first.map { it.partIndex })
        assertTrue(first.all { it.partCount == first.size })
    }

    @Test
    fun batchSearchIgnoresProvidersThatRequireSettlementPlaceholder() {
        val directProvider = ExternalSearchProvider(
            id = "direct",
            title = "Direct",
            countryCode = null,
            urlTemplate = "https://example.test/search/{settlement}?q={terms}",
            priority = 10,
        )

        assertTrue(
            ExternalSearchBatchBuilder.build(
                providers = listOf(directProvider),
                settlementNames = listOf("Alpha", "Beta"),
                terms = "house",
            ).isEmpty(),
        )
    }

    @Test
    fun batchSearchMakesProviderUnavailableWhenOneNameCannotFitBound() {
        val provider = ExternalSearchProvider(
            id = "google",
            title = "Google",
            countryCode = null,
            urlTemplate = "https://www.google.com/search?q={query}",
            priority = 10,
        )

        val actions =
            ExternalSearchBatchBuilder.build(
                providers = listOf(provider),
                settlementNames = listOf("A".repeat(200)),
                maxUrlLength = 80,
            )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun catalogRejectsNonHttpsTemplates() {
        val payload =
            """{"providers":[{"id":"bad","title":"Bad","countryCode":null,"urlTemplate":"http://example.test?q={query}","priority":1}]}"""
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ExternalSearchProviderCatalog.decode(payload)
        }
    }
}
