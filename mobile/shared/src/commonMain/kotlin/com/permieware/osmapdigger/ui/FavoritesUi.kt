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
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.workspace.FavoriteCandidateTransfer
import kotlin.math.roundToInt

/** Desktop-first notebook surface for browsing, selecting, re-analyzing, and removing favorites. */
@Composable
internal fun FavoritesPanel(
    modifier: Modifier,
    favorites: List<FavoriteSettlement>,
    settlementsById: Map<String, Settlement>,
    rankedResults: List<ScoredSettlement>,
    onOpen: (Settlement) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
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
                    ) {
                        Text(strings.selectAll)
                    }
                    TextButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { selectedIds = emptySet() },
                    ) {
                        Text(strings.clearSelection)
                    }
                    TextButton(
                        enabled = transfer != null,
                        onClick = {
                            transfer?.let { imported ->
                                onCopySelectedToImport(imported.sourceText, imported.settlementIds)
                            }
                        },
                    ) {
                        Text(strings.copyFavoritesToImport(selectedIds.size))
                    }
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
                        val settlement = settlementsById[favorite.settlementId]
                        val ranked = rankedById[favorite.settlementId]
                        val selectable = settlement != null
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
                                    checked = favorite.settlementId in selectedIds,
                                    enabled = selectable,
                                    onCheckedChange = { checked ->
                                        selectedIds =
                                            if (checked) {
                                                selectedIds + favorite.settlementId
                                            } else {
                                                selectedIds - favorite.settlementId
                                            }
                                    },
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
                                        val context =
                                            listOfNotNull(
                                                settlement.placeType,
                                                settlement.population?.let(strings.population),
                                            ).joinToString(" · ")
                                        val coordinates =
                                            "${NumberFormatter.compact(settlement.location.latitude)}, " +
                                                NumberFormatter.compact(settlement.location.longitude)
                                        Text(
                                            listOf(context, coordinates).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        if (ranked != null) {
                                            val rank = rankById[favorite.settlementId]
                                            Text(
                                                strings.favoriteCurrentAnalysis(
                                                    rank,
                                                    ranked.score.value?.roundToInt(),
                                                    ranked.score.coverage.roundToInt(),
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        } else {
                                            Text(
                                                strings.favoriteNotInCurrentResults,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (settlement != null) {
                                            TextButton(onClick = { onOpen(settlement) }) {
                                                Text(strings.openFavorite)
                                            }
                                        }
                                        TextButton(onClick = { onRemove(favorite.settlementId) }) {
                                            Text(strings.remove)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
