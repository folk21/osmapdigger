package com.permieware.osmapdigger.external

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import kotlin.test.Test
import kotlin.test.assertTrue

class PropertySearchLinksTest {
    @Test
    fun googleQueryContainsSiteRestrictionAndEncodedSettlement() {
        val settlement =
            Settlement(
                id = "1",
                name = "Test Village",
                localName = null,
                englishName = null,
                placeType = "village",
                population = null,
                location = GeoPoint(0.0, 0.0),
            )

        val url = PropertySearchLinks.build(ExternalSearchProvider.GOOGLE, settlement, site = "kufar.by")
        assertTrue(url.contains("google.com/search"))
        assertTrue(url.contains("site%3Akufar.by"))
    }
}
