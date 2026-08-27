package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.domain.SearchRequest

/** Complete shared analysis request: hard eligibility search plus ranking preferences. */
data class SettlementAnalysisRequest(
    val search: SearchRequest,
    val preferences: List<MetricPreference>,
) {
    init {
        require(preferences.map { it.metricId }.distinct().size == preferences.size) {
            "Analysis preferences must use unique metric IDs"
        }
    }
}
