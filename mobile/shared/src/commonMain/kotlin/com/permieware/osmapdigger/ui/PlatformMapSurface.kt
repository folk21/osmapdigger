package com.permieware.osmapdigger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.map.MapPackage

/** Optional platform-owned map renderer used when the shared default renderer is unavailable. */
interface PlatformMapSurface {
    @Composable
    fun Render(
        modifier: Modifier,
        datasetInfo: DatasetInfo?,
        mapPackage: MapPackage,
        results: List<Settlement>,
        selected: Settlement?,
        focusRequest: Long = 0L,
        onSettlementActivated: ((String) -> Unit)? = null,
        onMapLocationActivated: ((GeoPoint) -> Unit)? = null,
    )
}
