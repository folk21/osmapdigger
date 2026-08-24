package com.permieware.osmapdigger.dataset

/**
 * Platform independent dataset lifecycle contract.
 *
 * Desktop and Android provide different storage implementations but use the same API.
 */
interface DatasetManager {
    fun installedDatasets(): List<DatasetMetadata>

    fun currentDataset(): DatasetMetadata?

    fun openDataset(datasetId: String)

    fun closeCurrentDataset()
}
