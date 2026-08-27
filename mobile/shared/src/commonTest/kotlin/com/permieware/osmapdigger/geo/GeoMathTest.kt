package com.permieware.osmapdigger.geo

import com.permieware.osmapdigger.domain.GeoPoint
import kotlin.test.Test
import kotlin.test.assertTrue

class GeoMathTest {
    @Test
    fun distanceIsApproximatelyKnownForOneLongitudeDegreeAtEquator() {
        val distance = GeoMath.distanceKm(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        assertTrue(distance in 111.0..112.0)
    }
}
