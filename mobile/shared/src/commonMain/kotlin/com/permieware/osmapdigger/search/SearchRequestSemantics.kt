package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.SearchRequest
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.geo.GeoMath

/** Shared validation and exact-radius semantics reused by search and ranked analysis. */
internal object SearchRequestSemantics {
    fun validate(request: SearchRequest) {
        val radius = request.radiusKm
        require(radius == null || request.center != null) {
            "Radius requires a selected center settlement"
        }
        require(radius == null || (radius.isFinite() && radius > 0.0)) {
            "Radius must be a finite positive number"
        }
    }

    fun effectiveConditions(request: SearchRequest): List<SearchCondition> =
        request.conditions.filter { it.isEffective }

    fun boundingBox(
        request: SearchRequest,
    ): Pair<ClosedFloatingPointRange<Double>, ClosedFloatingPointRange<Double>>? {
        val center = request.center ?: return null
        val radius = request.radiusKm ?: return null
        return GeoMath.boundingBox(center.location, radius)
    }

    fun matchesExactRadius(
        request: SearchRequest,
        settlement: Settlement,
    ): Boolean {
        val center = request.center ?: return true
        val radius = request.radiusKm ?: return true
        return GeoMath.distanceKm(center.location, settlement.location) <= radius
    }

    fun resultLimit(request: SearchRequest): Int = request.limit.coerceAtLeast(1)
}
