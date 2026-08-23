package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.Settlement

/**
 * Desktop map fallback used by the initial Desktop implementation.
 *
 * Desktop intentionally stays independent from MapLibre native bindings for now. This keeps
 * the JVM host portable and stable while the Android application uses the real MapLibre map.
 * Search, filters, settlement details and external-property workflows remain fully available.
 */
@Composable
internal actual fun MapPanel(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    styleJson: String?,
    results: List<Settlement>,
    selected: Settlement?,
) {
    Box(modifier.padding(24.dp)) {
        Card {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Map unavailable on this Desktop host", style = MaterialTheme.typography.titleMedium)
                Text(
                    "MapLibre Compose does not provide a native Intel macOS runtime. " +
                        "Search, filters, settlement details, and generated dataset data remain available.",
                )
                datasetInfo?.let {
                    Text("Dataset: ${it.displayName}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Results in current search: ${results.size}", style = MaterialTheme.typography.bodySmall)
                selected?.let {
                    Text(
                        "Selected: ${it.name} (${it.location.latitude}, ${it.location.longitude})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (styleJson == null) {
                    Text("The installed package also has no generated map artifact.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
