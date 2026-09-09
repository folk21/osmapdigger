package com.permieware.osmapdigger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.presentation.MetricDisplayNameResolver
import com.permieware.osmapdigger.presentation.PreferenceDataAvailabilityBuilder
import com.permieware.osmapdigger.presentation.PreferenceDataAvailabilityPresenter
import com.permieware.osmapdigger.presentation.PreferenceQualityBaseline
import com.permieware.osmapdigger.presentation.ScoreExplanationBuilder
import com.permieware.osmapdigger.presentation.SettlementPlaceTypeResolver
import kotlin.math.roundToInt

/** Compact result-list heading with direct access to the persistent favorites notebook. */
@Composable
internal fun RankedResultsHeader(
    rankedResultCount: Int,
    favoriteCount: Int,
    hasEnabledPreferences: Boolean,
    onOpenFavorites: () -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(strings.rankedResults, style = MaterialTheme.typography.titleMedium)
            if (hasEnabledPreferences) {
                Text(
                    strings.rankedResultsExplanation,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                strings.favoriteCount(favoriteCount),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(rankedResultCount.toString(), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onOpenFavorites) {
                Text(strings.openFavorites)
            }
        }
    }
}

/**
 * Scan-friendly ranked result card with authoritative score presentation and independent persistent
 * favorite membership. Favorite changes never modify candidate source, filters, ranking, or map focus.
 */
@Composable
internal fun RankedSettlementCard(
    rank: Int,
    result: ScoredSettlement,
    definitions: Map<String, MetricDefinition>,
    comparisonBaseline: PreferenceQualityBaseline,
    hasEnabledPreferences: Boolean,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    displayName: String,
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    val explanation = remember(result.score, definitions, comparisonBaseline) {
        ScoreExplanationBuilder.build(result.score, definitions, comparisonBaseline)
    }
    val dataAvailability = remember(result.score) { PreferenceDataAvailabilityBuilder.build(result.score) }
    val missingPreferenceTitles = remember(dataAvailability, definitions, language) {
        PreferenceDataAvailabilityPresenter.missingTitles(dataAvailability, definitions, language)
    }
    val advantage =
        explanation.advantage?.let { contribution ->
            val definition = definitions[contribution.contribution.metricId]
            val title = definition?.let { MetricDisplayNameResolver.resolveCompact(it, language) } ?: contribution.title
            comparativeCue("↑", title, contribution.contribution.quality)
        }
    val compromise =
        explanation.compromise?.let { contribution ->
            val definition = definitions[contribution.contribution.metricId]
            val title = definition?.let { MetricDisplayNameResolver.resolveCompact(it, language) } ?: contribution.title
            comparativeCue("↓", title, contribution.contribution.quality)
        }

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        border =
            if (selected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("#$rank", style = MaterialTheme.typography.labelLarge)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(displayName, style = MaterialTheme.typography.titleSmall)
                    if (hasEnabledPreferences) {
                        Text(
                            result.score.value?.let { strings.scoreValue(it.roundToInt()) } ?: strings.scoreUnavailable,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                if (hasEnabledPreferences) result.score.value?.let { score ->
                    LinearProgressIndicator(
                        progress = { (score / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val context =
                    listOfNotNull(
                        SettlementPlaceTypeResolver.resolve(result.settlement.placeType, language),
                        result.settlement.population?.let(strings.population),
                    ).joinToString(" · ")
                if (context.isNotBlank()) {
                    Text(context, style = MaterialTheme.typography.bodySmall)
                }
                if (hasEnabledPreferences && result.score.value != null && result.score.coverage < 99.5) {
                    Text(
                        strings.dataCoverage(result.score.coverage.roundToInt()),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (
                    hasEnabledPreferences &&
                    result.score.value == null &&
                    dataAvailability.isScoreUnavailableBecauseAllPreferenceDataIsMissing
                ) {
                    Text(
                        strings.missingPreferenceSummary(
                            dataAvailability.missingPreferenceCount,
                            dataAvailability.totalPreferenceCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${strings.missingPreferenceCriteria} ${missingPreferenceTitles.joinToString()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                advantage?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                compromise?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                if (selected) {
                    Text(strings.selected, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(
                    checked = favorite,
                    onCheckedChange = onFavoriteChanged,
                )
                Text(strings.addToFavorites, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun comparativeCue(
    marker: String,
    title: String,
    quality: Double?,
): String {
    val points = quality?.let { " ${(it * 100.0).roundToInt()}/100" }.orEmpty()
    return "$marker $title$points"
}
