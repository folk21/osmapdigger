package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.external.ExternalLinkOpener
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.external.ExternalSearchUrlBuilder
import com.permieware.osmapdigger.presentation.MetricValueFormatter
import com.permieware.osmapdigger.presentation.NumberFormatter

@Composable
internal fun SettlementDetailsCard(
    details: SettlementDetails,
    datasetInfo: DatasetInfo?,
    externalLinks: ExternalLinkOpener,
    searchProviders: List<ExternalSearchProvider>,
    onExternalSearchSettingsSave: (List<ExternalSearchProviderTermsUpdate>) -> Unit,
    settlementDisplayName: (Settlement) -> String = { it.name },
) {
    val strings = LocalUiStrings.current
    var externalSearchSettingsOpen by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.settlementDetails, style = MaterialTheme.typography.labelLarge)
            Text(settlementDisplayName(details.settlement), style = MaterialTheme.typography.titleMedium)
            Text(
                strings.coordinates(
                    NumberFormatter.compact(details.settlement.location.latitude),
                    NumberFormatter.compact(details.settlement.location.longitude),
                ),
                style = MaterialTheme.typography.bodySmall,
            )

            details.metrics
                .groupBy { it.definition.group }
                .forEach { (group, values) ->
                    Text(group, style = MaterialTheme.typography.labelLarge)
                    values.take(16).forEach { metric ->
                        Text(
                            "${metric.definition.title}: ${MetricValueFormatter.known(metric.value, metric.definition.unit)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

            if (searchProviders.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.externalPropertySearch, style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { externalSearchSettingsOpen = !externalSearchSettingsOpen }) {
                        Text(strings.externalSearchSettings)
                    }
                }
                if (externalSearchSettingsOpen) {
                    ExternalSearchTermsSettings(
                        providers = searchProviders,
                        datasetTerms = datasetInfo?.propertySearchTerms ?: "property",
                        onSave = { updates ->
                            onExternalSearchSettingsSave(updates)
                            externalSearchSettingsOpen = false
                        },
                        onClose = { externalSearchSettingsOpen = false },
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    }
}

