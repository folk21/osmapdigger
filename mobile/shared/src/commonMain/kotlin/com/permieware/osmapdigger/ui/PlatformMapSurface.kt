package com.permieware.osmapdigger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.runtime.MapPackage

/** Optional platform-owned map renderer used when the shared default renderer is unavailable. */
interface PlatformMapSurface {
    @Composable
    fun Render(
        modifier: Modifier,
        datasetInfo: DatasetInfo?,
        mapPackage: MapPackage,
        results: List<Settlement>,
        selected: Settlement?,
    )
}
