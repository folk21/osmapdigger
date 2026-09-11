package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementName
import com.permieware.osmapdigger.domain.SettlementSearchEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementDisplayNameResolverTest {
    @Test
    fun preferredLanguageAliasWinsOverCanonicalName() {
        val entry = entry(
            canonical = "Віцебск",
            names = listOf(
                name("Віцебск", "be"),
                name("Витебск", "ru"),
                name("Vitebsk", "en"),
            ),
        )

        assertEquals("Витебск", SettlementDisplayNameResolver.resolve(entry, "ru"))
        assertEquals("Vitebsk", SettlementDisplayNameResolver.resolve(entry, "en"))
    }

    @Test
    fun canonicalNameIsUsedWhenRequestedLanguageIsUnavailable() {
        val entry = entry(canonical = "Мядзел", names = listOf(name("Мядзел", "be")))
        assertEquals("Мядзел", SettlementDisplayNameResolver.resolve(entry, "ru"))
    }

    private fun entry(canonical: String, names: List<SettlementName>) =
        SettlementSearchEntry(
            settlement = Settlement("s1", canonical, null, null, "town", null, GeoPoint(0.0, 0.0)),
            names = names,
        )

    private fun name(value: String, language: String) =
        SettlementName(value, value.lowercase(), language = language, kind = "localized")
}
