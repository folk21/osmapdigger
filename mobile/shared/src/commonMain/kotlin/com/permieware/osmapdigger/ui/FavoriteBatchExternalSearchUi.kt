package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.external.ExternalSearchBatchAction

/** Presents generated Favorites batch-search queries without launching any action automatically. */
@Composable
internal fun FavoriteBatchExternalSearchPanel(
    modifier: Modifier,
    actions: List<ExternalSearchBatchAction>,
    selectedCount: Int,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalUiStrings.current
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.externalSearch, style = MaterialTheme.typography.titleMedium)
                Text(strings.selectedFavorites(selectedCount), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onBack) { Text(strings.favorites) }
        }
        Text(strings.batchExternalSearchHint, style = MaterialTheme.typography.bodySmall)

        if (actions.isEmpty()) {
            Text(strings.noBatchExternalProviders)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    actions,
                    key = { "${it.providerId}:${it.providerCountryCode.orEmpty()}:${it.partIndex}" },
                ) { action ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OutlinedButton(
                            onClick = { onOpen(action.url) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val part = if (action.partCount > 1) " ${action.partIndex}/${action.partCount}" else ""
                            Text("${action.providerTitle}$part")
                        }
                        Text(
                            action.settlementNames.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
