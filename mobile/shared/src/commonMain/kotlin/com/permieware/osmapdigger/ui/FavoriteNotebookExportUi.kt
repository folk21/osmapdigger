package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.notebook.FavoriteNotebookExportBuilder
import com.permieware.osmapdigger.notebook.FavoriteNotebookExportOutcome
import com.permieware.osmapdigger.notebook.FavoriteNotebookExporter
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Explicit portable export action for the whole dataset-scoped Favorites notebook. */
@Composable
internal fun FavoriteNotebookExportAction(
    modifier: Modifier = Modifier,
    favorites: List<FavoriteSettlement>,
    settlementsById: Map<String, Settlement>,
    exporter: FavoriteNotebookExporter?,
    settlementDisplayName: (Settlement) -> String,
) {
    if (favorites.isEmpty() || exporter == null) return

    val strings = LocalUiStrings.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val bundle = remember(favorites, settlementsById, settlementDisplayName) {
        val datasetId = favorites.first().datasetId
        FavoriteNotebookExportBuilder.build(
            datasetId = datasetId,
            favorites = favorites,
            currentDisplayNames = favorites.mapNotNull { favorite ->
                settlementsById[favorite.settlementId]
                    ?.let(settlementDisplayName)
                    ?.let { favorite.settlementId to it }
            }.toMap(),
        )
    }

    Column(modifier) {
        TextButton(
            onClick = {
                status = null
                scope.launch {
                    try {
                        when (exporter.export(bundle)) {
                            FavoriteNotebookExportOutcome.COMPLETED -> status = strings.favoriteExportCompleted
                            FavoriteNotebookExportOutcome.CANCELLED -> Unit
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Throwable) {
                        status = strings.favoriteExportFailed
                    }
                }
            },
        ) {
            Text(strings.exportFavorites)
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
    }
}
