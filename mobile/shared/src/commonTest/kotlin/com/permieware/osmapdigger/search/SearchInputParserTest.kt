package com.permieware.osmapdigger.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchInputParserTest {
    @Test
    fun positiveRadiusAcceptsOnlyFinitePositiveNumbers() {
        assertEquals(2.5, SearchInputParser.positiveRadiusKm("2.5"))
        assertNull(SearchInputParser.positiveRadiusKm("0"))
        assertNull(SearchInputParser.positiveRadiusKm("-1"))
        assertNull(SearchInputParser.positiveRadiusKm("NaN"))
        assertNull(SearchInputParser.positiveRadiusKm("invalid"))
    }
}
