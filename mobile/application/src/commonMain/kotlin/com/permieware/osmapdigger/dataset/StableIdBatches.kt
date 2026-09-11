package com.permieware.osmapdigger.dataset

/**
 * Partition stable IDs deterministically for repository adapters with bounded query parameter counts.
 *
 * IDs are sorted before batching so platform query chunking cannot affect result determinism.
 */
object StableIdBatches {
    fun partition(ids: Set<String>, maxBatchSize: Int): List<List<String>> {
        require(maxBatchSize > 0) { "Stable ID batch size must be positive" }
        require(ids.none { it.isBlank() }) { "Stable IDs must not be blank" }
        return ids.sorted().chunked(maxBatchSize)
    }
}
