package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.notebook.FavoriteAnalysisSnapshot
import com.permieware.osmapdigger.notebook.FavoritePreferenceSnapshot
import com.permieware.osmapdigger.notebook.FavoriteRequiredCriterionSnapshot
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.workspace.FavoriteCandidateTransfer
import kotlin.math.roundToInt

/** Desktop-first notebook surface for browsing, selecting, re-analyzing, annotating, and removing favorites. */
@Composable
internal fun FavoritesPanel(
    modifier: Modifier,
    favorites: List<FavoriteSettlement>,
    settlementsById: Map<String, Settlement>,
    rankedResults: List<ScoredSettlement>,
    onOpen: (Settlement) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onNoteChanged: (String, String?) -> Unit,
    onUpdateSnapshot: (String) -> Unit,
    onCopySelectedToImport: (String, List<String>) -> Unit,
    onClose: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    val rankedById = remember(rankedResults) { rankedResults.associateBy { it.settlement.id } }
    val rankById = remember(rankedResults) {
        rankedResults.mapIndexed { index, result -> result.settlement.id to index + 1 }.toMap()
    }
    val availableIds = remember(settlementsById) { settlementsById.keys.toSet() }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(favorites, availableIds) {
        val retainedFavoriteIds = favorites.mapTo(hashSetOf()) { it.settlementId }
        selectedIds = selectedIds.filterTo(linkedSetOf()) { it in retainedFavoriteIds && it in availableIds }
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
                    TextButton(
                        enabled = transfer != null,
                        onClick = {
                            transfer?.let { imported ->
                                onCopySelectedToImport(imported.sourceText, imported.settlementIds)
                            }
                        },
                    ) { Text(strings.copyFavoritesToImport(selectedIds.size)) }
                }
            }

            HorizontalDivider()

            if (favorites.isEmpty()) {
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
                            ranked = rankedById[favorite.settlementId],
                            rank = rankById[favorite.settlementId],
                            selected = favorite.settlementId in selectedIds,
                            onSelectedChanged = { checked ->
                                selectedIds = if (checked) selectedIds + favorite.settlementId else selectedIds - favorite.settlementId
                            },
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
    ranked: ScoredSettlement?,
    rank: Int?,
    selected: Boolean,
    onSelectedChanged: (Boolean) -> Unit,
    onOpen: (Settlement) -> Unit,
    onRemove: () -> Unit,
    onNoteChanged: (String?) -> Unit,
    onUpdateSnapshot: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
    var editingNote by remember(favorite.settlementId) { mutableStateOf(false) }
    var noteText by remember(favorite.settlementId, favorite.note) { mutableStateOf(favorite.note.orEmpty()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
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
                Text(
                    settlement?.let(settlementDisplayName) ?: favorite.settlementName,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (settlement == null) {
                    Text(strings.favoriteUnavailable, style = MaterialTheme.typography.bodySmall)
                } else {
                    val context = listOfNotNull(settlement.placeType, settlement.population?.let(strings.population)).joinToString(" · ")
                    val coordinates = "${NumberFormatter.compact(settlement.location.latitude)}, ${NumberFormatter.compact(settlement.location.longitude)}"
                    Text(listOf(context, coordinates).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (ranked != null) {
                        Text(
                            strings.favoriteCurrentAnalysis(rank, ranked.score.value?.roundToInt(), ranked.score.coverage.roundToInt()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        Text(strings.favoriteNotInCurrentResults, style = MaterialTheme.typography.labelSmall)
                    }
                }

                FavoriteSnapshotSummary(favorite.analysisSnapshot)

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
                    TextButton(onClick = { editingNote = true }) { Text(strings.editFavoriteNote) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settlement != null) {
                        TextButton(onClick = { onOpen(settlement) }) { Text(strings.openFavorite) }
                    }
                    TextButton(
                        enabled = ranked != null,
                        onClick = onUpdateSnapshot,
                    ) { Text(strings.updateFavoriteSnapshot) }
                    TextButton(onClick = onRemove) { Text(strings.remove) }
                }
                if (ranked == null) {
                    Text(strings.snapshotUpdateUnavailable, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
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
        strings.favoriteSnapshotSummary(snapshot.scoreValue?.roundToInt(), snapshot.coverage.roundToInt()),
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
