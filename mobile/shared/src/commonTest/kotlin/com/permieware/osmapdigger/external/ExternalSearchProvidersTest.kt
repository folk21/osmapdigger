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
    fun catalogRejectsNonHttpsTemplates() {
        val payload =
            """{"providers":[{"id":"bad","title":"Bad","countryCode":null,"urlTemplate":"http://example.test?q={query}","priority":1}]}"""
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ExternalSearchProviderCatalog.decode(payload)
        }
    }

}
