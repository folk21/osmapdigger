package com.permieware.osmapdigger.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    @Test
    fun optionalRadiusTreatsBlankAndZeroAsUnset() {
        assertIs<OptionalRadiusInput.Unset>(SearchInputParser.optionalRadiusKm(""))
        assertIs<OptionalRadiusInput.Unset>(SearchInputParser.optionalRadiusKm("  "))
        assertIs<OptionalRadiusInput.Unset>(SearchInputParser.optionalRadiusKm("0"))
        assertIs<OptionalRadiusInput.Unset>(SearchInputParser.optionalRadiusKm("0.0"))
    }

    @Test
    fun optionalRadiusDistinguishesPositiveValuesFromInvalidInput() {
        assertEquals(OptionalRadiusInput.Value(5.0), SearchInputParser.optionalRadiusKm("5"))
        assertIs<OptionalRadiusInput.Invalid>(SearchInputParser.optionalRadiusKm("-1"))
        assertIs<OptionalRadiusInput.Invalid>(SearchInputParser.optionalRadiusKm("NaN"))
        assertIs<OptionalRadiusInput.Invalid>(SearchInputParser.optionalRadiusKm("invalid"))
    }
}
