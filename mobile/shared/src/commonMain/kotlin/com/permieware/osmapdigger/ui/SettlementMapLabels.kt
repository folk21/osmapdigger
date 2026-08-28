package com.permieware.osmapdigger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.Settlement

/**
 * Renders lightweight runtime settlement labels above native MapLibre surfaces.
 *
 * Native MapLibre text layers require an offline glyph source that the current package format does
 * not provide. Compose labels therefore keep ranked settlement names offline without changing the
 * generated map style or dataset format.
 */
@Composable
internal fun BoxScope.SettlementMapLabels(
    results: List<Settlement>,
    selected: Settlement?,
    zoom: Double,
    positionOf: (Settlement) -> DpOffset?,
) {
    if (zoom >= RESULT_LABEL_MIN_ZOOM) {
        results.asSequence()
            .filterNot { it.id == selected?.id }
            .forEach { settlement ->
                positionOf(settlement)?.let { offset ->
                    SettlementMapLabel(
                        settlement = settlement,
                        offset = offset,
                        selected = false,
                    )
                }
            }
    }

    selected?.let { settlement ->
        positionOf(settlement)?.let { offset ->
            SettlementMapLabel(
                settlement = settlement,
                offset = offset,
                selected = true,
            )
        }
    }
}

@Composable
private fun SettlementMapLabel(
    settlement: Settlement,
    offset: DpOffset,
    selected: Boolean,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        }
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Text(
        text = settlement.name,
        modifier =
            Modifier
                .offset(x = offset.x + 7.dp, y = offset.y - 22.dp)
                .background(containerColor, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        color = contentColor,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

internal const val RESULT_LABEL_MIN_ZOOM = 8.0
