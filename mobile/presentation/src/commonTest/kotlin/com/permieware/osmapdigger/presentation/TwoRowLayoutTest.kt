package com.permieware.osmapdigger.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class TwoRowLayoutTest {
    @Test
    fun splitsCriteriaIntoTwoBalancedRowsWithoutReordering() {
        assertEquals(
            listOf(listOf(1, 2, 3), listOf(4, 5)),
            TwoRowLayout.split(listOf(1, 2, 3, 4, 5)),
        )
    }

    @Test
    fun keepsSingleItemInOneRow() {
        assertEquals(listOf(listOf("a")), TwoRowLayout.split(listOf("a")))
    }
}
