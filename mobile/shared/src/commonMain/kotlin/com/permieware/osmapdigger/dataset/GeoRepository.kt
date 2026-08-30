package com.permieware.osmapdigger.dataset

import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricPreferenceDefault
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementAnalysisCandidate
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.domain.SettlementSearchEntry

/** Platform-provided read-only analytical dataset repository. */
interface GeoRepository {
    suspend fun datasetInfo(): DatasetInfo
    suspend fun metricDefinitions(): List<MetricDefinition>

    /**
     * Return dataset-provided generic preference defaults when the package defines them.
     *
     * Legacy format-v1 packages may not contain the additive preference-default table;
     * in that case repositories return an empty list rather than inventing defaults.
     */
    suspend fun preferenceDefaults(): List<MetricPreferenceDefault>

    suspend fun settlementSearchEntries(): List<SettlementSearchEntry>

    suspend fun searchCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        limit: Int,
    ): List<Settlement>

    /**
     * Return all hard-filter-eligible candidates plus only requested scoring metrics.
     *
     * Implementations must not apply an unrelated final result limit here: shared
     * analysis performs exact radius filtering, scoring, ranking, and final limiting.
     * Missing requested metrics remain absent from each candidate metric map.
     */
    suspend fun analysisCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        scoringMetricIds: Set<String>,
    ): List<SettlementAnalysisCandidate>

    suspend fun details(settlementId: String): SettlementDetails
}
