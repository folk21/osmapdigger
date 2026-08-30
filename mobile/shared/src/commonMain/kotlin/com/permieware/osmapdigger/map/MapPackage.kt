package com.permieware.osmapdigger.map

/** Platform-resolved local map assets for the opened dataset. */
data class MapPackage(
    val styleJson: String?,
    val localMapUri: String? = null,
)
