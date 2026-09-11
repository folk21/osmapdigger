package com.permieware.osmapdigger.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatterTest {
    @Test
    fun compactFormattingKeepsIntegersAndRoundsFractionalValues() {
        assertEquals("2", NumberFormatter.compact(2.0))
        assertEquals("2.35", NumberFormatter.compact(2.345))
    }
}
