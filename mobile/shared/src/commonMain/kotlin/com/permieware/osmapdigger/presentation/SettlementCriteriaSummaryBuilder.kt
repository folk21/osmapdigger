package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference

/** One compact metric value selected for the Desktop settlement summary. */
data class SettlementCriteriaSummaryItem(
    val metricId: String,
    val title: String,
    val unit: String,
    val value: Double?,
)

/**
 * Select the most relevant search metrics for compact settlement presentation.
 *
 * Effective hard constraints come first, then enabled ranking preferences. Metric IDs are
 * de-duplicated while preserving that priority. Missing metric data remains unknown.
 */
object SettlementCriteriaSummaryBuilder {
    fun build(
        details: SettlementDetails,
        definitions: Map<String, MetricDefinition>,
        conditions: List<SearchCondition>,
        preferences: List<EffectiveMetricPreference>,
        limit: Int = 3,
    ): List<SettlementCriteriaSummaryItem> {
        require(limit >= 0) { "Summary metric limit must not be negative" }
        if (limit == 0) return emptyList()

        val metricValues = details.metrics.associate { it.definition.id to it.value }
        val metricIds = linkedSetOf<String>()
        conditions.asSequence()
            .filter { it.isEffective }
            .forEach { metricIds += it.metricId }
        preferences.asSequence()
            .filter { it.enabled }
            .forEach { metricIds += it.metricId }

        return metricIds
            .asSequence()
            .take(limit)
            .map { metricId ->
                val definition = definitions[metricId]
                SettlementCriteriaSummaryItem(
                    metricId = metricId,
                    title = definition?.title ?: metricId,
                    unit = definition?.unit.orEmpty(),
                    value = metricValues[metricId],
                )
            }
            .toList()
    }
}
