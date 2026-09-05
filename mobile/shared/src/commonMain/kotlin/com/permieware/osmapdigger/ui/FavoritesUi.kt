package com.permieware.osmapdigger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.analysis.RankedSettlementResult
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.external.ExternalSearchBatchBuilder
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.notebook.FavoriteAnalysisSnapshot
import com.permieware.osmapdigger.notebook.FavoriteNotebookExporter
import com.permieware.osmapdigger.notebook.FavoritePreferenceSnapshot
import com.permieware.osmapdigger.notebook.FavoriteRequiredCriterionSnapshot
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.presentation.SettlementPlaceTypeResolver
import com.permieware.osmapdigger.workspace.FavoriteCandidateTransfer
import kotlin.math.roundToInt

/** Desktop-first notebook surface for browsing, selecting, re-analyzing, annotating, and removing favorites. */
@Composable
internal fun FavoritesPanel(
    modifier: Modifier,
    favorites: List<FavoriteSettlement>,
    settlementsById: Map<String, Settlement>,
    rankedCandidatesBySettlementId: Map<String, RankedSettlementResult>,
    hasEnabledPreferences: Boolean,
    activeSettlementId: String?,
    onSelect: (Settlement) -> Unit,
    onOpen: (Settlement) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onNoteChanged: (String, String?) -> Unit,
    onUpdateSnapshot: (String) -> Unit,
    onCopySelectedToImport: (String, List<String>) -> Unit,
    searchProviders: List<ExternalSearchProvider>,
    propertySearchTerms: String,
    onExternalSearchUrlOpen: (String) -> Unit,
    onExternalSearchSettingsSave: (List<ExternalSearchProviderTermsUpdate>) -> Unit,
    favoriteNotebookExporter: FavoriteNotebookExporter?,
    onClose: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    val availableIds = remember(settlementsById) { settlementsById.keys.toSet() }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var batchSearchOpen by remember { mutableStateOf(false) }

    LaunchedEffect(favorites, availableIds) {
        val retainedFavoriteIds = favorites.mapTo(hashSetOf()) { it.settlementId }
        selectedIds = selectedIds.filterTo(linkedSetOf()) { it in retainedFavoriteIds && it in availableIds }
    }
    LaunchedEffect(selectedIds) {
        if (selectedIds.isEmpty()) batchSearchOpen = false
    }

    val selectableIds = remember(favorites, availableIds) {
        favorites.mapNotNullTo(linkedSetOf()) { favorite ->
            favorite.settlementId.takeIf { it in availableIds }
        }
    }
    val transfer = remember(favorites, selectedIds, availableIds) {
        FavoriteCandidateTransfer.build(
            favorites = favorites,
            selectedSettlementIds = selectedIds,
            availableSettlementIds = availableIds,
        )
    }
    val selectedNames = remember(favorites, selectedIds, settlementsById, settlementDisplayName) {
        favorites.mapNotNull { favorite ->
            if (favorite.settlementId !in selectedIds) return@mapNotNull null
            settlementsById[favorite.settlementId]?.let(settlementDisplayName)
        }
    }
    val batchSearchActions = remember(searchProviders, selectedNames, propertySearchTerms) {
        ExternalSearchBatchBuilder.build(
            providers = searchProviders,
            settlementNames = selectedNames,
            terms = propertySearchTerms,
        )
    }

    Surface(modifier) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(strings.favorites, style = MaterialTheme.typography.headlineSmall)
                    Text(strings.favoriteCount(favorites.size), style = MaterialTheme.typography.bodySmall)
                    if (favorites.isNotEmpty()) {
                        Text(strings.selectedFavorites(selectedIds.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row {
                    FavoriteNotebookExportAction(
                        favorites = favorites,
                        settlementsById = settlementsById,
                        exporter = favoriteNotebookExporter,
                        settlementDisplayName = settlementDisplayName,
                    )
                    if (favorites.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text(strings.clearFavorites) }
                    }
                    TextButton(onClick = onClose) { Text(strings.close) }
                }
            }

            if (favorites.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = selectableIds.isNotEmpty() && selectedIds != selectableIds,
                        onClick = { selectedIds = selectableIds },
                    ) { Text(strings.selectAll) }
                    TextButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { selectedIds = emptySet() },
                    ) { Text(strings.clearSelection) }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = transfer != null,
                        onClick = {
                            transfer?.let { imported ->
                                onCopySelectedToImport(imported.sourceText, imported.settlementIds)
                            }
                        },
                    ) { Text(strings.copyFavoritesToImport(selectedIds.size)) }
                    TextButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { batchSearchOpen = true },
                    ) { Text(strings.externalSearch) }
                }
            }

            HorizontalDivider()

            if (batchSearchOpen) {
                FavoriteBatchExternalSearchPanel(
                    modifier = Modifier.fillMaxSize(),
                    actions = batchSearchActions,
                    providers = searchProviders,
                    propertySearchTerms = propertySearchTerms,
                    selectedCount = selectedIds.size,
                    onOpen = onExternalSearchUrlOpen,
                    onSettingsSave = onExternalSearchSettingsSave,
                    onBack = { batchSearchOpen = false },
                )
            } else if (favorites.isEmpty()) {
                Text(strings.favoritesEmpty)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(favorites, key = { "${it.datasetId}:${it.settlementId}" }) { favorite ->
                        FavoriteCard(
                            favorite = favorite,
                            settlement = settlementsById[favorite.settlementId],
                            currentAnalysis = rankedCandidatesBySettlementId[favorite.settlementId],
                            hasEnabledPreferences = hasEnabledPreferences,
                            selected = favorite.settlementId in selectedIds,
                            active = favorite.settlementId == activeSettlementId,
                            onSelectedChanged = { checked ->
                                selectedIds = if (checked) selectedIds + favorite.settlementId else selectedIds - favorite.settlementId
                            },
                            onSelect = onSelect,
                            onOpen = onOpen,
                            onRemove = { onRemove(favorite.settlementId) },
                            onNoteChanged = { note -> onNoteChanged(favorite.settlementId, note) },
                            onUpdateSnapshot = { onUpdateSnapshot(favorite.settlementId) },
                            settlementDisplayName = settlementDisplayName,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    favorite: FavoriteSettlement,
    settlement: Settlement?,
    currentAnalysis: RankedSettlementResult?,
    hasEnabledPreferences: Boolean,
    selected: Boolean,
    active: Boolean,
    onSelectedChanged: (Boolean) -> Unit,
    onSelect: (Settlement) -> Unit,
    onOpen: (Settlement) -> Unit,
    onRemove: () -> Unit,
    onNoteChanged: (String?) -> Unit,
    onUpdateSnapshot: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    var editingNote by remember(favorite.settlementId) { mutableStateOf(false) }
    var noteText by remember(favorite.settlementId, favorite.note) { mutableStateOf(favorite.note.orEmpty()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = if (active) 3.dp else 1.dp,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = selected,
                enabled = settlement != null,
                onCheckedChange = onSelectedChanged,
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    Modifier.clickable(enabled = settlement != null) { settlement?.let(onSelect) },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            settlement?.let(settlementDisplayName) ?: favorite.settlementName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (active) {
                            Text(strings.favoriteCurrentSelection, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (settlement == null) {
                        Text(strings.favoriteUnavailable, style = MaterialTheme.typography.bodySmall)
                    } else {
                        val context =
                            listOfNotNull(
                                SettlementPlaceTypeResolver.resolve(settlement.placeType, language),
                                settlement.population?.let(strings.population),
                            ).joinToString(" · ")
                        val coordinates =
                            strings.coordinates(
                                NumberFormatter.compact(settlement.location.latitude),
                                NumberFormatter.compact(settlement.location.longitude),
                            )
                        Text(listOf(context, coordinates).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (currentAnalysis != null) {
                            Text(
                                strings.favoriteCurrentAnalysis(
                                    currentAnalysis.rank,
                                    currentAnalysis.result.score.value?.roundToInt(),
                                    currentAnalysis.result.score.coverage.roundToInt(),
                                    hasEnabledPreferences,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            Text(strings.favoriteNotInCurrentResults, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    FavoriteSnapshotSummary(favorite.analysisSnapshot)
                }

                Spacer(Modifier.height(8.dp))

                if (editingNote) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(strings.favoriteNote) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onNoteChanged(noteText.trim().takeIf { it.isNotEmpty() })
                                editingNote = false
                            },
                        ) { Text(strings.saveFavoriteNote) }
                        TextButton(
                            onClick = {
                                noteText = favorite.note.orEmpty()
                                editingNote = false
                            },
                        ) { Text(strings.cancelFavoriteNote) }
                    }
                } else {
                    favorite.note?.let { Text("${strings.favoriteNote}: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }

            Column(
                Modifier.widthIn(min = 88.dp, max = 112.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                if (!editingNote) {
                    FavoriteAction(strings.editFavoriteNote) { editingNote = true }
                }
                if (settlement != null) {
                    FavoriteAction(strings.openFavorite) { onOpen(settlement) }
                }
                FavoriteAction(
                    label = strings.updateFavoriteSnapshot,
                    enabled = currentAnalysis != null,
                    onClick = onUpdateSnapshot,
                )
                FavoriteAction(strings.remove, onClick = onRemove)
            }
        }
    }
}

@Composable
private fun FavoriteAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun FavoriteSnapshotSummary(snapshot: FavoriteAnalysisSnapshot?) {
    val strings = LocalUiStrings.current
    if (snapshot == null) {
        Text(strings.noFavoriteSnapshot, style = MaterialTheme.typography.labelSmall)
        return
    }

    Text(
        strings.favoriteSnapshotSummary(
            snapshot.scoreValue?.roundToInt(),
            snapshot.coverage.roundToInt(),
            snapshot.preferences.isNotEmpty(),
        ),
        style = MaterialTheme.typography.labelMedium,
    )
    val area = buildList {
        snapshot.centerSettlementName?.let { add(it) }
        snapshot.radiusKm?.let { add("${NumberFormatter.compact(it)} km") }
    }.joinToString(" · ")
    if (area.isNotBlank()) Text(area, style = MaterialTheme.typography.labelSmall)

    if (snapshot.requiredCriteria.isNotEmpty()) {
        Text(
            "${strings.favoriteSnapshotRequired}: ${snapshot.requiredCriteria.joinToString("; ", transform = ::requiredCriterionText)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (snapshot.preferences.isNotEmpty()) {
        Text(
            "${strings.favoriteSnapshotPreferences}: ${snapshot.preferences.joinToString("; ", transform = ::preferenceText)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun requiredCriterionText(item: FavoriteRequiredCriterionSnapshot): String {
    val bounds = listOfNotNull(
        item.minValue?.let { ">=${NumberFormatter.compact(it)}" },
        item.maxValue?.let { "<=${NumberFormatter.compact(it)}" },
    ).joinToString(" ")
    return listOf(item.title, bounds, item.unit).filter { it.isNotBlank() }.joinToString(" ")
}

private fun preferenceText(item: FavoritePreferenceSnapshot): String {
    val raw = item.rawValue?.let(NumberFormatter::compact) ?: "?"
    val contribution = item.scoreContribution?.let(NumberFormatter::compact) ?: "?"
    val direction = if (item.direction == PreferredDirection.LOWER) "↓" else "↑"
    val thresholds =
        "$direction target ${NumberFormatter.compact(item.targetValue)} / limit ${NumberFormatter.compact(item.limitValue)}"
    return "${item.title}: $raw ${item.unit} · $thresholds · w${item.weight} · +$contribution"
}
