package com.permieware.osmapdigger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.analysis.SettlementScore
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.external.ExternalLinkOpener
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.external.ExternalSearchUrlBuilder
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference
import com.permieware.osmapdigger.presentation.MetricValueFormatter
import com.permieware.osmapdigger.presentation.MetricDisplayNameResolver
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.presentation.ScoreExplanationBuilder
import com.permieware.osmapdigger.presentation.SettlementCriteriaSummaryBuilder
import com.permieware.osmapdigger.presentation.SettlementPlaceTypeResolver
import com.permieware.osmapdigger.presentation.TwoRowLayout
import com.permieware.osmapdigger.presentation.UiStrings
import kotlin.math.roundToInt

@Composable
internal fun ResultsStatusPanel(
    modifier: Modifier,
    resultCount: Int,
    running: Boolean,
) {
    val strings = LocalUiStrings.current
    Surface(modifier) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(strings.searchResults, style = MaterialTheme.typography.labelLarge)
                Text(
                    strings.settlementsFound(resultCount),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (running) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(strings.updating.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

internal enum class DesktopSettlementPaneMode {
    SUMMARY,
    DETAILS,
    EXTERNAL_SEARCH,
}

@Composable
internal fun SettlementSummaryPanel(
    modifier: Modifier,
    details: SettlementDetails,
    score: SettlementScore?,
    definitions: Map<String, MetricDefinition>,
    conditions: List<SearchCondition>,
    effectivePreferences: List<EffectiveMetricPreference>,
    resultCount: Int,
    running: Boolean,
    hasExternalSearch: Boolean,
    onDetails: () -> Unit,
    onExternalSearch: () -> Unit,
    onClose: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    Card(modifier) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(settlementDisplayName(details.settlement), style = MaterialTheme.typography.titleLarge)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        strings.settlementsFound(resultCount),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (running) {
                        Text(strings.updating.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            val context =
                listOfNotNull(
                    SettlementPlaceTypeResolver.resolve(details.settlement.placeType, language),
                    details.settlement.population?.let(strings.population),
                ).joinToString(" · ")
            val coordinates =
                strings.coordinates(
                    NumberFormatter.compact(details.settlement.location.latitude),
                    NumberFormatter.compact(details.settlement.location.longitude),
                )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOf(context, coordinates).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDetails) { Text(strings.details) }
                    if (hasExternalSearch) {
                        TextButton(onClick = onExternalSearch) { Text(strings.externalSearch) }
                    }
                    TextButton(onClick = onClose) { Text(strings.close) }
                }
            }

            if (effectivePreferences.any { it.enabled }) {
                val settlementScore = score
                val scoreText =
                    settlementScore?.value?.let { value ->
                        strings.scoreAndCoverage(value.roundToInt(), settlementScore.coverage.roundToInt())
                    } ?: strings.scoreUnavailable
                Text(scoreText, style = MaterialTheme.typography.titleSmall)
            }

            val criteriaSummary = remember(details, definitions, conditions, effectivePreferences, language) {
                SettlementCriteriaSummaryBuilder.build(
                    details = details,
                    definitions = definitions,
                    conditions = conditions,
                    preferences = effectivePreferences,
                ).map { item ->
                    val definition = definitions[item.metricId]
                    if (definition == null) item else item.copy(title = MetricDisplayNameResolver.resolve(definition, language))
                }
            }
            if (criteriaSummary.isNotEmpty()) {
                Text(strings.activeCriteria, style = MaterialTheme.typography.labelMedium)
                val rows = TwoRowLayout.split(criteriaSummary)
                Column(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rows.forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            rowItems.forEach { metric ->
                                Column(Modifier.width(180.dp)) {
                                    Text(metric.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    Text(
                                        MetricValueFormatter.optional(metric.value, metric.unit, strings.unknownValue),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettlementDetailedInfoPanel(
    modifier: Modifier,
    details: SettlementDetails,
    score: SettlementScore?,
    hasEnabledPreferences: Boolean,
    definitions: Map<String, MetricDefinition>,
    onBack: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    Card(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(settlementDisplayName(details.settlement), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text(strings.summary) }
            }

            val context =
                listOfNotNull(
                    SettlementPlaceTypeResolver.resolve(details.settlement.placeType, language),
                    details.settlement.population?.let(strings.population),
                ).joinToString(" · ")
            if (context.isNotBlank()) {
                Text(context, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                strings.coordinates(
                    NumberFormatter.compact(details.settlement.location.latitude),
                    NumberFormatter.compact(details.settlement.location.longitude),
                ),
                style = MaterialTheme.typography.bodySmall,
            )

            if (hasEnabledPreferences) {
                val settlementScore = score
                Text(strings.score, style = MaterialTheme.typography.labelLarge)
                val scoreText =
                    settlementScore?.value?.let { value ->
                        strings.scoreAndCoverage(value.roundToInt(), settlementScore.coverage.roundToInt())
                    } ?: strings.scoreUnavailable
                Text(scoreText, style = MaterialTheme.typography.titleMedium)
                val explanation = settlementScore?.let { currentScore ->
                    remember(currentScore, definitions) {
                        ScoreExplanationBuilder.build(currentScore, definitions)
                    }
                }
                explanation?.strongest?.let { strongest ->
                    Text(
                        "${strings.strongest}: ${contributionLabel(strongest.title, strongest.unit, strongest.contribution.rawValue, strongest.contribution.scoreContribution, strings)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                explanation?.weakest?.let { weakest ->
                    Text(
                        "${strings.weakest}: ${contributionLabel(weakest.title, weakest.unit, weakest.contribution.rawValue, weakest.contribution.scoreContribution, strings)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (explanation != null && explanation.unknown.isNotEmpty()) {
                    Text(
                        "${strings.unknown}: ${explanation.unknown.joinToString { it.title }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            details.metrics
                .groupBy { it.definition.group }
                .forEach { (group, values) ->
                    Text(group, style = MaterialTheme.typography.labelLarge)
                    values.forEach { metric ->
                        Text(
                            "${metric.definition.title}: ${MetricValueFormatter.known(metric.value, metric.definition.unit)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
        }
    }
}

@Composable
internal fun SettlementExternalSearchPanel(
    modifier: Modifier,
    details: SettlementDetails,
    datasetInfo: DatasetInfo?,
    externalLinks: ExternalLinkOpener,
    searchProviders: List<ExternalSearchProvider>,
    onExternalSearchSettingsSave: (List<ExternalSearchProviderTermsUpdate>) -> Unit,
    onBack: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    var settingsOpen by remember { mutableStateOf(false) }
    Card(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(strings.externalSearch, style = MaterialTheme.typography.headlineSmall)
                    Text(settlementDisplayName(details.settlement), style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    if (searchProviders.isNotEmpty()) {
                        TextButton(onClick = { settingsOpen = !settingsOpen }) {
                            Text(strings.externalSearchSettings)
                        }
                    }
                    TextButton(onClick = onBack) { Text(strings.summary) }
                }
            }

            if (settingsOpen) {
                ExternalSearchTermsSettings(
                    providers = searchProviders,
                    datasetTerms = datasetInfo?.propertySearchTerms ?: "property",
                    onSave = { updates ->
                        onExternalSearchSettingsSave(updates)
                        settingsOpen = false
                    },
                    onClose = { settingsOpen = false },
                )
            } else if (searchProviders.isEmpty()) {
                Text(strings.noExternalProviders)
            } else {
                searchProviders.forEach { provider ->
                    OutlinedButton(
                        onClick = {
                            externalLinks.open(
                                ExternalSearchUrlBuilder.build(
                                    provider = provider,
                                    settlement = details.settlement,
                                    terms = datasetInfo?.propertySearchTerms ?: "property",
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(provider.title)
                    }
                }
            }
        }
    }
}

private fun contributionLabel(
    title: String,
    unit: String,
    rawValue: Double?,
    scoreContribution: Double?,
    strings: UiStrings,
): String {
    val value = MetricValueFormatter.optional(rawValue, unit, strings.unknownValue)
    val points = scoreContribution?.let { strings.points(NumberFormatter.compact(it)) } ?: strings.unknownContribution
    return "$title · $value · $points"
}

