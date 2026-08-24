package com.permieware.osmapdigger.dataset

/**
 * Metadata stored inside an OMD package.
 */
data class DatasetMetadata(
    val id: String,
    val countryCode: String,
    val name: String,
    val version: String,
)
