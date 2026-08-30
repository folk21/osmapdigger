package com.permieware.osmapdigger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.map.MapScaleCalculator
import kotlin.math.roundToInt

/** Displays a compact metric scale bar for the current native MapLibre camera. */
@Composable
internal fun MapScaleIndicator(
    latitude: Double,
    zoom: Double,
    modifier: Modifier = Modifier,
) {
    val targetWidth = 104.dp
    val density = LocalDensity.current
    val targetWidthPx = with(density) { targetWidth.toPx().toDouble() }
    val scale = MapScaleCalculator.calculate(latitude, zoom, targetWidthPx) ?: return
    val barWidth = targetWidth * scale.widthFractionOfTarget.toFloat()

    Card(modifier) {
        Column(
            Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(formatScaleDistance(scale.distanceMeters), style = MaterialTheme.typography.labelSmall)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .width(barWidth)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

private fun formatScaleDistance(distanceMeters: Double): String =
    if (distanceMeters >= 1000.0) {
        val kilometers = distanceMeters / 1000.0
        if (kilometers >= 10.0 || kilometers == kilometers.roundToInt().toDouble()) {
            "${kilometers.roundToInt()} km"
        } else {
            "${(kilometers * 10.0).roundToInt() / 10.0} km"
        }
    } else {
        "${distanceMeters.roundToInt()} m"
    }
