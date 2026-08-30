package com.permieware.osmapdigger.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A metric map-scale bar derived from the Web Mercator zoom and center latitude. */
data class MapScale(
    val distanceMeters: Double,
    val widthFractionOfTarget: Double,
) {
    init {
        require(distanceMeters > 0.0 && distanceMeters.isFinite()) { "Scale distance must be finite and positive." }
        require(widthFractionOfTarget in 0.0..1.0) { "Scale width fraction must be between 0 and 1." }
    }
}

/**
 * Calculates a conventional 1/2/5 metric scale bar for a Web Mercator map.
 *
 * The meters-per-pixel formula is the standard Web Mercator ground-resolution approximation at a
 * given latitude and zoom. The returned bar never exceeds the requested target width.
 */
object MapScaleCalculator {
    private const val EQUATOR_METERS_PER_PIXEL_AT_ZOOM_ZERO = 156543.03392804097

    fun calculate(
        latitude: Double,
        zoom: Double,
        targetWidthPx: Double,
    ): MapScale? {
        if (!latitude.isFinite() || !zoom.isFinite() || !targetWidthPx.isFinite() || targetWidthPx <= 0.0) return null

        val clampedLatitude = latitude.coerceIn(-85.05112878, 85.05112878)
        val metersPerPixel =
            EQUATOR_METERS_PER_PIXEL_AT_ZOOM_ZERO *
                cos(clampedLatitude * PI / 180.0) /
                2.0.pow(zoom)
        if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) return null

        val maximumDistance = metersPerPixel * targetWidthPx
        val distance = niceDistanceAtMost(maximumDistance) ?: return null
        return MapScale(
            distanceMeters = distance,
            widthFractionOfTarget = (distance / maximumDistance).coerceIn(0.0, 1.0),
        )
    }

    private fun niceDistanceAtMost(maximumDistance: Double): Double? {
        if (!maximumDistance.isFinite() || maximumDistance <= 0.0) return null
        val exponent = floor(log10(maximumDistance))
        val magnitude = 10.0.pow(exponent)
        val normalized = maximumDistance / magnitude
        val multiplier = when {
            normalized >= 5.0 -> 5.0
            normalized >= 2.0 -> 2.0
            normalized >= 1.0 -> 1.0
            else -> 5.0 / 10.0
        }
        return multiplier * magnitude
    }
}
