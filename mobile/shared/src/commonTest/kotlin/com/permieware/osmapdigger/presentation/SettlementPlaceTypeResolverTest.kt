package com.permieware.osmapdigger.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettlementPlaceTypeResolverTest {
    @Test
    fun localizesKnownOsmPlaceTypesForRussianPresentation() {
        assertEquals("город", SettlementPlaceTypeResolver.resolve("city", UiLanguage.RUSSIAN))
        assertEquals("город", SettlementPlaceTypeResolver.resolve("town", UiLanguage.RUSSIAN))
        assertEquals("деревня", SettlementPlaceTypeResolver.resolve("village", UiLanguage.RUSSIAN))
        assertEquals("деревня", SettlementPlaceTypeResolver.resolve("hamlet", UiLanguage.RUSSIAN))
        assertEquals("отдельное поселение", SettlementPlaceTypeResolver.resolve("isolated_dwelling", UiLanguage.RUSSIAN))
    }

    @Test
    fun preservesEnglishAndUnknownForwardCompatibleValues() {
        assertEquals("hamlet", SettlementPlaceTypeResolver.resolve("hamlet", UiLanguage.ENGLISH))
        assertEquals("future_place", SettlementPlaceTypeResolver.resolve("future_place", UiLanguage.RUSSIAN))
        assertNull(SettlementPlaceTypeResolver.resolve(null, UiLanguage.RUSSIAN))
    }
}
