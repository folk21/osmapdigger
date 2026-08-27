package com.permieware.osmapdigger.geo

import com.permieware.osmapdigger.domain.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Reusable WGS84 geographic calculations independent from search orchestration. */
object GeoMath {
    private const val EARTH_RADIUS_KM = 6371.0088

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val deltaLat = Math.toRadians(b.latitude - a.latitude)
        val deltaLon = Math.toRadians(b.longitude - a.longitude)
        val haversine =
            sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(haversine)))
    }

    fun boundingBox(
        center: GeoPoint,
        radiusKm: Double,
    ): Pair<ClosedFloatingPointRange<Double>, ClosedFloatingPointRange<Double>> {
        require(radiusKm > 0.0 && radiusKm.isFinite()) { "Radius must be a positive finite value" }
        val latitudeDelta = radiusKm / 111.32
        val cosine = cos(Math.toRadians(center.latitude))
        val longitudeDelta = if (kotlin.math.abs(cosine) < 1e-12) 180.0 else radiusKm / (111.32 * cosine)
        val latitudeRange =
            max(-90.0, center.latitude - latitudeDelta)..min(90.0, center.latitude + latitudeDelta)
        val longitudeRange =
            max(-180.0, center.longitude - longitudeDelta)..min(180.0, center.longitude + longitudeDelta)
        return latitudeRange to longitudeRange
    }
}
