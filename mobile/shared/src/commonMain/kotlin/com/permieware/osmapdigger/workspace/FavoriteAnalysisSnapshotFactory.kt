package com.permieware.osmapdigger.workspace

import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.notebook.FavoriteAnalysisSnapshotDraft
import com.permieware.osmapdigger.notebook.FavoritePreferenceSnapshot
import com.permieware.osmapdigger.notebook.FavoriteRequiredCriterionSnapshot
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference

/** Build the immutable notebook snapshot from the already authoritative analysis state. */
object FavoriteAnalysisSnapshotFactory {
    fun create(
        datasetId: String,
        result: ScoredSettlement,
        definitions: List<MetricDefinition>,
        conditions: List<SearchCondition>,
        effectivePreferences: List<EffectiveMetricPreference>,
        center: Settlement?,
        radiusKm: Double?,
    ): FavoriteAnalysisSnapshotDraft {
        val definitionsById = definitions.associateBy { it.id }
        val contributionById = result.score.contributions.associateBy { it.metricId }

        val required = conditions.filter { it.isEffective }.map { condition ->
            val definition = definitionsById[condition.metricId]
            FavoriteRequiredCriterionSnapshot(
                metricId = condition.metricId,
                title = definition?.title ?: condition.metricId,
                unit = definition?.unit.orEmpty(),
                minValue = condition.minValue,
                maxValue = condition.maxValue,
            )
        }

        val preferences = effectivePreferences.filter { it.enabled }.map { effective ->
            val definition = definitionsById[effective.metricId]
            val contribution = contributionById[effective.metricId]
            FavoritePreferenceSnapshot(
                metricId = effective.metricId,
                title = definition?.title ?: effective.metricId,
                unit = definition?.unit.orEmpty(),
                direction = effective.preference.direction,
                targetValue = effective.preference.targetValue,
                limitValue = effective.preference.limitValue,
                weight = effective.preference.weight,
                rawValue = contribution?.rawValue,
                quality = contribution?.quality,
                weightedContribution = contribution?.weightedContribution,
                scoreContribution = contribution?.scoreContribution,
            )
        }

        return FavoriteAnalysisSnapshotDraft(
            datasetId = datasetId,
            settlementId = result.settlement.id,
            settlementName = result.settlement.name,
            scoreValue = result.score.value,
            coverage = result.score.coverage,
            knownWeight = result.score.knownWeight,
            totalWeight = result.score.totalWeight,
            centerSettlementId = center?.id,
            centerSettlementName = center?.name,
            radiusKm = radiusKm,
            requiredCriteria = required,
            preferences = preferences,
        )
    }
}
