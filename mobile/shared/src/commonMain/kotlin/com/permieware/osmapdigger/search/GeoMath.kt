package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.domain.GeoPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Geographic distance helpers used after SQL bounding-box candidate reduction. */
object GeoMath {
    private const val EarthRadiusKm = 6371.0088

    fun distanceKm(first: GeoPoint, second: GeoPoint): Double {
        val lat1 = first.latitude.toRadians()
        val lat2 = second.latitude.toRadians()
        val dLat = (second.latitude - first.latitude).toRadians()
        val dLon = (second.longitude - first.longitude).toRadians()

        val a =
            sin(dLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)

        return EarthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun boundingBox(
        center: GeoPoint,
        radiusKm: Double,
    ): Pair<ClosedFloatingPointRange<Double>, ClosedFloatingPointRange<Double>> {
        val latitudeDelta = radiusKm / 111.32
        val cosine = cos(center.latitude.toRadians()).coerceAtLeast(0.01)
        val longitudeDelta = radiusKm / (111.32 * cosine)

        return (center.latitude - latitudeDelta..center.latitude + latitudeDelta) to
            (center.longitude - longitudeDelta..center.longitude + longitudeDelta)
    }

    private fun Double.toRadians(): Double = this / 180.0 * PI
}
