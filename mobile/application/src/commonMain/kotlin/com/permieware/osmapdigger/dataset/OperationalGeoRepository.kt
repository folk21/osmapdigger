package com.permieware.osmapdigger.dataset

import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricPreferenceDefault
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementAnalysisCandidate
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.domain.SettlementSearchEntry
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary

/** Runtime decorator that converts platform database exceptions into one shared operational contract. */
class OperationalGeoRepository(
    private val delegate: GeoRepository,
) : GeoRepository {
    override suspend fun datasetInfo(): DatasetInfo = databaseBoundary("read dataset metadata") { delegate.datasetInfo() }

    override suspend fun metricDefinitions(): List<MetricDefinition> =
        databaseBoundary("read metric definitions") { delegate.metricDefinitions() }

    override suspend fun preferenceDefaults(): List<MetricPreferenceDefault> =
        databaseBoundary("read preference defaults") { delegate.preferenceDefaults() }

    override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> =
        databaseBoundary("read settlement search index") { delegate.settlementSearchEntries() }

    override suspend fun searchCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        limit: Int,
    ): List<Settlement> =
        databaseBoundary("search settlement candidates") {
            delegate.searchCandidates(conditions, latitudeRange, longitudeRange, limit)
        }

    override suspend fun analysisCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        scoringMetricIds: Set<String>,
        candidateSettlementIds: Set<String>?,
    ): List<SettlementAnalysisCandidate> =
        databaseBoundary("read settlement analysis candidates") {
            delegate.analysisCandidates(
                conditions,
                latitudeRange,
                longitudeRange,
                scoringMetricIds,
                candidateSettlementIds,
            )
        }

    override suspend fun details(settlementId: String): SettlementDetails =
        databaseBoundary("read settlement details") { delegate.details(settlementId) }

    private inline fun <T> databaseBoundary(context: String, block: () -> T): T =
        operationalBoundary(OperationalFailureKind.DATABASE, context, block)
}
