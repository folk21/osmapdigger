package com.permieware.osmapdigger.desktop.map

import com.permieware.osmapdigger.domain.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Bounded same-origin interaction protocol between MapLibre GL JS and the Desktop host. */
internal object LocalWebMapInteraction {
    const val SETTLEMENT_PATH = "/interaction/settlement"
    const val MAP_LOCATION_PATH = "/interaction/location"
    const val MAX_BODY_BYTES = 4096
    const val SETTLEMENT_HIT_RADIUS_PX = 12

    fun decodeSettlement(body: String): String? =
        runCatching {
            Json.parseToJsonElement(body)
                .jsonObject["id"]
                ?.jsonPrimitive
                ?.content
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()

    fun decodeMapLocation(body: String): GeoPoint? =
        runCatching {
            val objectValue = Json.parseToJsonElement(body).jsonObject
            val latitude = objectValue["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val longitude = objectValue["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            if (
                latitude == null || longitude == null ||
                !latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) {
                null
            } else {
                GeoPoint(latitude = latitude, longitude = longitude)
            }
        }.getOrNull()
}
