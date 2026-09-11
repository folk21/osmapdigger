package com.permieware.osmapdigger.dataset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StableIdBatchesTest {
    @Test
    fun partitionsSortedStableIdsDeterministically() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c")),
            StableIdBatches.partition(setOf("c", "a", "b"), maxBatchSize = 2),
        )
    }

    @Test
    fun rejectsBlankIds() {
        assertFailsWith<IllegalArgumentException> {
            StableIdBatches.partition(setOf("ok", ""), maxBatchSize = 2)
        }
    }
}
