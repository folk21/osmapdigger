package com.permieware.osmapdigger.map

import com.permieware.osmapdigger.domain.Settlement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Serializes settlement points into engine-neutral GeoJSON used by map renderers. */
object MapOverlayGeoJson {
    /** Build a transient point FeatureCollection; this output is never analytical source data. */
    fun build(settlements: List<Settlement>): String =
        buildJsonObject {
            put("type", "FeatureCollection")
            put(
                "features",
                JsonArray(
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
                    },
                ),
            )
        }.toString()
}
