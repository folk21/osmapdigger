package com.permieware.osmapdigger.dataset

/**
 * Application dataset configuration.
 *
 * The model is shared between Desktop and Android. Platform code decides how configuration
 * is loaded and where packages are stored.
 */
data class DatasetConfig(
    val defaultDatasetId: String,
    val datasets: Map<String, DatasetPackageConfig>,
)

data class DatasetPackageConfig(
    val packageName: String,
    val autoInstall: Boolean = false,
)
