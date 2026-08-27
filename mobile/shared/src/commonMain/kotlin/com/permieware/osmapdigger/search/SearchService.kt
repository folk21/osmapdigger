package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.geo.GeoMath

import com.permieware.osmapdigger.domain.SearchRequest
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.runtime.GeoRepository

/** Executes deterministic local settlement search. */
class SearchService(
    private val repository: GeoRepository,
) {
    /**
     * Execute one local search while keeping platform SQL free of spatial extensions.
     * Repository SQL performs metric and bounding-box reduction; this method performs
     * the exact radial check and final result limit in shared code.
     */
    suspend fun search(request: SearchRequest): List<Settlement> {
        val center = request.center
        val radius = request.radiusKm

        require(radius == null || center != null) {
            "Radius requires a selected center settlement"
        }
        require(radius == null || (radius.isFinite() && radius > 0.0)) {
            "Radius must be a finite positive number"
        }

        val bounds =
            if (center != null && radius != null) {
                GeoMath.boundingBox(center.location, radius)
            } else {
                null
            }

        val candidates =
            repository.searchCandidates(
                conditions = request.conditions.filter { it.isEffective },
                latitudeRange = bounds?.first,
                longitudeRange = bounds?.second,
                limit = request.limit.coerceAtLeast(1) * 4,
            )

        val filtered =
            if (center != null && radius != null) {
                candidates.filter { GeoMath.distanceKm(center.location, it.location) <= radius }
            } else {
                candidates
            }

        return filtered.take(request.limit.coerceAtLeast(1))
    }
}
