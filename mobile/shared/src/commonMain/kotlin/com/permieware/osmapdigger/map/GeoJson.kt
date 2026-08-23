package com.permieware.osmapdigger.map

import com.permieware.osmapdigger.domain.Settlement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Serializes search results into a minimal GeoJSON overlay for MapLibre. */
object ResultGeoJson {
    /**
     * Build a minimal point FeatureCollection for transient MapLibre overlays.
     * This output is presentation-only and never becomes persisted analytical data.
     */
    fun build(settlements: List<Settlement>): String {
        val features =
            settlements.map { settlement ->
                buildJsonObject {
                    put("type", "Feature")
                    put(
                        "geometry",
                        buildJsonObject {
                            put("type", "Point")
                            put(
                                "coordinates",
                                buildJsonArray {
                                    add(settlement.location.longitude)
                                    add(settlement.location.latitude)
                                },
                            )
                        },
                    )
                    put(
                        "properties",
                        buildJsonObject {
                            put("id", settlement.id)
                            put("name", settlement.name)
                        },
                    )
                }
            }

        return buildJsonObject {
            put("type", "FeatureCollection")
            put("features", JsonArray(features))
        }.toString()
    }
}
