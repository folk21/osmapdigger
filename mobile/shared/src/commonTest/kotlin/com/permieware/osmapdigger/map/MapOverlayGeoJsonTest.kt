package com.permieware.osmapdigger.map

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class MapOverlayGeoJsonTest {
    @Test
    fun exposesStableSettlementIdForRendererInteraction() {
        val settlement =
            Settlement(
                id = "node/42",
                name = "Example",
                localName = null,
                englishName = null,
                placeType = "village",
                population = null,
                location = GeoPoint(latitude = 42.0, longitude = 23.0),
            )

        val root = Json.parseToJsonElement(MapOverlayGeoJson.build(listOf(settlement))).jsonObject
        val feature = root.getValue("features").jsonArray.single().jsonObject
        val properties = feature.getValue("properties").jsonObject

        assertEquals("node/42", properties.getValue("id").jsonPrimitive.content)
    }
}
