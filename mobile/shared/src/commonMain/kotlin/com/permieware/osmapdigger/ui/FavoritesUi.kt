package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.notebook.FavoriteSettlement

/** Desktop-first notebook surface for browsing and removing persistent favorite settlements. */
@Composable
internal fun FavoritesPanel(
    modifier: Modifier,
    favorites: List<FavoriteSettlement>,
    settlementsById: Map<String, Settlement>,
    onOpen: (Settlement) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    settlementDisplayName: (Settlement) -> String,
) {
    val strings = LocalUiStrings.current
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
                }
                Row {
                    if (favorites.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text(strings.clearFavorites) }
                    }
                    TextButton(onClick = onClose) { Text(strings.close) }
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
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    settlement?.let(settlementDisplayName) ?: favorite.settlementName,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (settlement == null) {
                                    Text(strings.favoriteUnavailable, style = MaterialTheme.typography.bodySmall)
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
