package com.permieware.osmapdigger.search

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
        SearchRequestSemantics.validate(request)
        val bounds = SearchRequestSemantics.boundingBox(request)
        val resultLimit = SearchRequestSemantics.resultLimit(request)

        val candidates =
            repository.searchCandidates(
                conditions = SearchRequestSemantics.effectiveConditions(request),
                latitudeRange = bounds?.first,
                longitudeRange = bounds?.second,
                limit = resultLimit * 4,
            )

        return candidates
            .filter { SearchRequestSemantics.matchesExactRadius(request, it) }
            .take(resultLimit)
    }
}
