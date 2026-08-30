package com.permieware.osmapdigger.runtime

import com.permieware.osmapdigger.dataset.GeoRepository
import com.permieware.osmapdigger.external.ExternalLinkOpener
import com.permieware.osmapdigger.map.MapPackage

/** Dataset-scoped dependencies assembled by a platform composition root. */
data class OsmapDiggerRuntime(
    val repository: GeoRepository,
    val mapPackage: MapPackage,
    val externalLinks: ExternalLinkOpener,
)
