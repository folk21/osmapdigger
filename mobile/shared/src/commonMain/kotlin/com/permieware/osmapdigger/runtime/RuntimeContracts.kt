package com.permieware.osmapdigger.runtime

import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.domain.SettlementSearchEntry

/** Platform-provided local dataset repository. */
interface GeoRepository {
    suspend fun datasetInfo(): DatasetInfo
    suspend fun metricDefinitions(): List<MetricDefinition>
    suspend fun settlementSearchEntries(): List<SettlementSearchEntry>
    suspend fun searchCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        limit: Int,
    ): List<Settlement>
    suspend fun details(settlementId: String): SettlementDetails
}

/** Platform-resolved local map assets. */
data class MapPackage(
    val styleJson: String?,
    val localMapUri: String? = null,
)

/** Runtime dependencies injected by the platform host. */
data class OsmapDiggerRuntime(
    val repository: GeoRepository,
    val mapPackage: MapPackage,
    val externalLinks: ExternalLinkOpener,
)

/** Opens an explicit user-selected URL in the platform browser. */
fun interface ExternalLinkOpener {
    fun open(url: String)
}
