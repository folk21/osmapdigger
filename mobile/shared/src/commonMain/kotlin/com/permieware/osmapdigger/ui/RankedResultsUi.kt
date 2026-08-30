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
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.presentation.ScoreExplanationBuilder
import com.permieware.osmapdigger.presentation.UiStrings
import kotlin.math.roundToInt

/** Compact result-list heading with direct access to the persistent favorites notebook. */
@Composable
internal fun RankedResultsHeader(
    rankedResultCount: Int,
    favoriteCount: Int,
    onOpenFavorites: () -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(strings.rankedResults, style = MaterialTheme.typography.titleMedium)
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
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    displayName: String,
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    val explanation = remember(result.score, definitions) {
        ScoreExplanationBuilder.build(result.score, definitions)
    }
    val strongest =
        explanation.strongest?.let { contribution ->
            val definition = definitions[contribution.contribution.metricId]
            val title = definition?.let { MetricDisplayNameResolver.resolve(it, language) } ?: contribution.title
            contributionCue(strings.strongest, title, contribution.contribution.scoreContribution, strings)
        }
    val weakest =
        explanation.weakest?.let { contribution ->
            val definition = definitions[contribution.contribution.metricId]
            val title = definition?.let { MetricDisplayNameResolver.resolve(it, language) } ?: contribution.title
            contributionCue(strings.weakest, title, contribution.contribution.scoreContribution, strings)
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
                    Text(
                        strings.scoreValue(result.score.value?.roundToInt()),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                result.score.value?.let { score ->
                    LinearProgressIndicator(
                        progress = { (score / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val context =
                    listOfNotNull(
                        result.settlement.placeType,
                        result.settlement.population?.let(strings.population),
                    ).joinToString(" · ")
                if (context.isNotBlank()) {
                    Text(context, style = MaterialTheme.typography.bodySmall)
                }
                if (result.score.coverage < 99.5) {
                    Text(
                        strings.dataCoverage(result.score.coverage.roundToInt()),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                strongest?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                weakest?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
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

private fun contributionCue(
    prefix: String,
    title: String,
    scoreContribution: Double?,
    strings: UiStrings,
): String {
    val points = scoreContribution?.let { strings.points(NumberFormatter.compact(it)) }
    return if (points == null) "$prefix: $title" else "$prefix: $title · $points"
}
