package com.permieware.osmapdigger.desktop.map

import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalWebMapPageTest {
    @Test
    fun rewritesPmtilesVectorSourceToLoopbackTilesWithoutChangingOtherSources() {
        val style =
            """{
              "version":8,
              "sources":{
                "local":{"type":"vector","url":"pmtiles://file:///map.pmtiles"},
                "other":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}
              },
              "layers":[]
            }""".trimIndent()

        val rewritten =
            Json.parseToJsonElement(
                LocalWebMapPage.rewriteStyle(
                    styleJson = style,
                    baseUrl = "http://127.0.0.1:1234",
                    minZoom = 2,
                    maxZoom = 14,
                ),
            ).jsonObject
        val local = rewritten.getValue("sources").jsonObject.getValue("local").jsonObject
        val other = rewritten.getValue("sources").jsonObject.getValue("other").jsonObject

        assertFalse("url" in local)
        assertEquals("http://127.0.0.1:1234/tiles/{z}/{x}/{y}.pbf", local.getValue("tiles").jsonArray.single().jsonPrimitive.content)
        assertEquals("2", local.getValue("minzoom").jsonPrimitive.content)
        assertEquals("14", local.getValue("maxzoom").jsonPrimitive.content)
        assertEquals("© OpenStreetMap contributors", local.getValue("attribution").jsonPrimitive.content)
        assertEquals("geojson", other.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun indexPageKeepsSameOriginInteractionAndMetricScaleContracts() {
        val html =
            LocalWebMapPage.indexHtml(
                DatasetInfo(
                    id = "test",
                    displayName = "Test",
                    countryCode = null,
                    center = GeoPoint(latitude = 53.9, longitude = 27.56),
                    initialZoom = 8.0,
                    hasMap = true,
                    propertySearchSite = null,
                    propertySearchTerms = "property",
                ),
            )

        assertTrue(html.contains("center: [27.56, 53.9]"))
        assertTrue(html.contains("unit: 'metric'"))
        assertTrue(html.contains(LocalWebMapInteraction.SETTLEMENT_PATH))
        assertTrue(html.contains(LocalWebMapInteraction.MAP_LOCATION_PATH))
        assertTrue(html.contains("const hitRadius = ${LocalWebMapInteraction.SETTLEMENT_HIT_RADIUS_PX};"))
    }
}
