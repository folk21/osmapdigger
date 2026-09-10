package com.permieware.osmapdigger.domain

/** One hard-filter-eligible settlement plus only requested sparse metric values. */
data class SettlementAnalysisCandidate(
    val settlement: Settlement,
    val metricValues: Map<String, Double>,
)
