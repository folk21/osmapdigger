package com.permieware.osmapdigger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement

/**
 * Platform map surface used by the shared application UI.
 *
 * Android and supported Desktop hosts provide a MapLibre implementation. Desktop hosts for
 * which MapLibre Compose has no published native runtime (currently including Intel macOS)
 * provide a graceful fallback so search and analytical workflows remain usable.
 */
@Composable
internal expect fun MapPanel(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    styleJson: String?,
    results: List<Settlement>,
    selected: Settlement?,
    onSettlementActivated: ((String) -> Unit)? = null,
    onMapLocationActivated: ((GeoPoint) -> Unit)? = null,
)
