package com.permieware.osmapdigger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.analysis.ImportedCandidateList
import com.permieware.osmapdigger.analysis.SettlementCandidateScope
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementSearchMatch
import com.permieware.osmapdigger.error.OperationalFailure
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.toOperationalFailure
import com.permieware.osmapdigger.search.SettlementImportResolution
import com.permieware.osmapdigger.search.SettlementImportReview
import com.permieware.osmapdigger.search.SettlementImportReviewer
import com.permieware.osmapdigger.search.SettlementListImportParser
import com.permieware.osmapdigger.search.SettlementListImportResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CandidateSourceSummary(
    candidateScope: SettlementCandidateScope,
    importedCandidateList: ImportedCandidateList?,
    onImportRequested: () -> Unit,
    onActivateImported: () -> Unit,
    onDeactivateImported: () -> Unit,
    onClearImported: () -> Unit,
) {
    val strings = LocalUiStrings.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(strings.candidateSource, style = MaterialTheme.typography.titleSmall)
            when (candidateScope) {
                SettlementCandidateScope.Dataset -> {
                    if (importedCandidateList == null) {
                        Text(strings.candidateSourceDataset, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onImportRequested) {
                            Text(strings.importSettlementList)
                        }
                    } else {
                        Text(
                            strings.candidateSourceImportedDisabled(importedCandidateList.settlementIds.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onActivateImported) {
                                Text(strings.activateImportedCandidates)
                            }
                            OutlinedButton(onClick = onImportRequested) {
                                Text(strings.replaceSettlementList)
                            }
                        }
                        TextButton(onClick = onClearImported) {
                            Text(strings.clearImportedList)
                        }
                    }
                }
                is SettlementCandidateScope.Imported -> {
                    Text(
                        strings.candidateSourceImported(candidateScope.settlementIds.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImportRequested) {
                            Text(strings.replaceSettlementList)
                        }
                        Button(onClick = onDeactivateImported) {
                            Text(strings.deactivateImportedCandidates)
                        }
                    }
                    TextButton(onClick = onClearImported) {
                        Text(strings.clearImportedList)
                    }
                }
            }
        }
    }
}

/** Full-sidebar review surface for the simple one-settlement-name-per-line import workflow. */
@Composable
internal fun CandidateImportPanel(
    modifier: Modifier,
    resolver: SettlementListImportResolver,
    initialText: String,
    center: Settlement?,
    radiusKm: Double?,
    onLoadTextFile: ((String) -> String?)?,
    onApply: (String, List<String>) -> Unit,
    onClose: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember(initialText) { mutableStateOf(initialText) }
    var review by remember { mutableStateOf<SettlementImportReview?>(null) }
    var selections by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var resolving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<OperationalFailure?>(null) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    fun replaceInput(nextText: String) {
        inputText = nextText
        review = null
        selections = emptyMap()
        validationMessage = null
        failure = null
    }

    fun startReview() {
        val lines = SettlementListImportParser.parse(inputText)
        if (lines.isEmpty()) {
            review = null
            selections = emptyMap()
            validationMessage = strings.noImportLines
            return
        }
        validationMessage = null
        failure = null
        resolving = true
        coroutineScope.launch {
            try {
                val nextReview =
                    resolver.resolve(
                        lines = lines,
                        center = center?.location,
                        radiusKm = radiusKm,
                    )
                review = nextReview
                selections = emptyMap()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failure = error.toOperationalFailure(
                    OperationalFailureKind.DATABASE,
                    "Could not resolve imported settlement names",
                )
            } finally {
                resolving = false
            }
        }
    }

    val reviewedIds =
        remember(review, selections) {
            review?.let { SettlementImportReviewer.reviewedSettlementIds(it, selections) }.orEmpty()
        }

    Surface(modifier = modifier, tonalElevation = 6.dp, shadowElevation = 6.dp) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.settlementListImportTitle, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClose) { Text(strings.close) }
            }
            Text(strings.settlementListImportInstructions, style = MaterialTheme.typography.bodySmall)
            if (center != null && radiusKm != null) {
                Text(
                    strings.importReviewUsesRadius(settlementDisplayName(center), radiusKm),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = ::replaceInput,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                label = { Text(strings.settlementListTextLabel) },
                enabled = !resolving,
                minLines = 5,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onLoadTextFile?.let { loadFile ->
                    OutlinedButton(
                        enabled = !resolving,
                        onClick = {
                            try {
                                loadFile(strings.settlementListImportTitle)?.let(::replaceInput)
                            } catch (error: Throwable) {
                                failure = error.toOperationalFailure(
                                    OperationalFailureKind.FILE_ACCESS,
                                    "Could not read imported settlement list file",
                                )
                            }
                        },
                    ) {
                        Text(strings.loadTextFile)
                    }
                }
                OutlinedButton(
                    enabled = !resolving && inputText.isNotEmpty(),
                    onClick = { replaceInput("") },
                ) {
                    Text(strings.clear)
                }
                Button(enabled = !resolving, onClick = ::startReview) {
                    Text(if (resolving) strings.resolvingNames else strings.resolveNames)
                }
            }
            validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            failure?.let {
                Text(strings.operationalFailureMessage(it.kind), color = MaterialTheme.colorScheme.error)
            }

            review?.let { currentReview ->
                HorizontalDivider()
                Text(
                    strings.importResolvedSummary(reviewedIds.size, currentReview.resolutions.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(currentReview.resolutions) { _, resolution ->
                        ImportResolutionCard(
                            resolution = resolution,
                            selectedSettlementIds = selections[resolution.line.normalizedText].orEmpty(),
                            onSelectionChanged = { settlementIds ->
                                selections =
                                    selections.toMutableMap().apply {
                                        if (settlementIds.isEmpty()) {
                                            remove(resolution.line.normalizedText)
                                        } else {
                                            put(resolution.line.normalizedText, settlementIds)
                                        }
                                    }
                            },
                            settlementDisplayName = settlementDisplayName,
                        )
                    }
                }
                Button(
                    onClick = { onApply(inputText, reviewedIds) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.applyImportedCandidates(reviewedIds.size))
                }
            }
        }
    }
}

@Composable
private fun ImportResolutionCard(
    resolution: SettlementImportResolution,
    selectedSettlementIds: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(resolution.line.sourceText, style = MaterialTheme.typography.titleSmall)
            when (resolution) {
                is SettlementImportResolution.Resolved -> {
                    Text(strings.exactMatch, style = MaterialTheme.typography.labelMedium)
                    MatchLabel(resolution.match, settlementDisplayName)
                }
                is SettlementImportResolution.Ambiguous -> {
                    Text(strings.ambiguousMatch, style = MaterialTheme.typography.labelMedium)
                    MultiSelectableMatches(
                        matches = resolution.matches,
                        selectedSettlementIds = selectedSettlementIds,
                        onSelectionChanged = onSelectionChanged,
                        settlementDisplayName = settlementDisplayName,
                    )
                }
                is SettlementImportResolution.Unresolved -> {
                    Text(strings.noExactMatch, style = MaterialTheme.typography.labelMedium)
                    if (resolution.suggestions.isNotEmpty()) {
                        Text(strings.suggestions, style = MaterialTheme.typography.labelSmall)
                    }
                    SingleSelectableMatches(
                        matches = resolution.suggestions,
                        selectedSettlementId = selectedSettlementIds.singleOrNull(),
                        onSelectionChanged = { settlementId ->
                            onSelectionChanged(
                                if (settlementId == null) emptySet() else setOf(settlementId),
                            )
                        },
                        settlementDisplayName = settlementDisplayName,
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiSelectableMatches(
    matches: List<SettlementSearchMatch>,
    selectedSettlementIds: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onSelectionChanged(matches.mapTo(linkedSetOf()) { it.settlement.id }) }) {
            Text(strings.selectAll)
        }
        TextButton(onClick = { onSelectionChanged(emptySet()) }) {
            Text(strings.clearSelection)
        }
    }
    matches.forEach { match ->
        val selected = match.settlement.id in selectedSettlementIds
        Row(
            Modifier.fillMaxWidth().clickable {
                onSelectionChanged(
                    if (selected) {
                        selectedSettlementIds - match.settlement.id
                    } else {
                        selectedSettlementIds + match.settlement.id
                    },
                )
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { checked ->
                    onSelectionChanged(
                        if (checked) {
                            selectedSettlementIds + match.settlement.id
                        } else {
                            selectedSettlementIds - match.settlement.id
                        },
                    )
                },
            )
            MatchLabel(match, settlementDisplayName)
        }
    }
}

@Composable
private fun SingleSelectableMatches(
    matches: List<SettlementSearchMatch>,
    selectedSettlementId: String?,
    onSelectionChanged: (String?) -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    Row(
        Modifier.fillMaxWidth().clickable { onSelectionChanged(null) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selectedSettlementId == null, onClick = { onSelectionChanged(null) })
        Text(strings.skipEntry)
    }
    matches.forEach { match ->
        Row(
            Modifier.fillMaxWidth().clickable { onSelectionChanged(match.settlement.id) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selectedSettlementId == match.settlement.id,
                onClick = { onSelectionChanged(match.settlement.id) },
            )
            MatchLabel(match, settlementDisplayName)
        }
    }
}

@Composable
private fun MatchLabel(
    match: SettlementSearchMatch,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(settlementDisplayName(match.settlement), style = MaterialTheme.typography.bodyMedium)
        val context =
            listOfNotNull(
                match.matchedName.takeUnless { it == settlementDisplayName(match.settlement) },
                match.settlement.placeType,
                match.settlement.population?.let(strings.population),
            ).joinToString(" · ")
        if (context.isNotBlank()) {
            Text(context, style = MaterialTheme.typography.bodySmall)
        }
    }
}
