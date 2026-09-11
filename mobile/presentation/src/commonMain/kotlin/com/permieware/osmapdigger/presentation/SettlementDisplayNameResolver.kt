package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.domain.SettlementSearchEntry

/** Resolve presentation names from persisted settlement aliases for the selected UI language. */
object SettlementDisplayNameResolver {
    fun resolve(entries: List<SettlementSearchEntry>, languageCode: String): Map<String, String> =
        entries.associate { entry ->
            entry.settlement.id to resolve(entry, languageCode)
        }

    fun resolve(entry: SettlementSearchEntry, languageCode: String): String {
        val localized =
            entry.names
                .asSequence()
                .filter { it.language.equals(languageCode, ignoreCase = true) }
                .sortedWith(compareBy({ nameKindPriority(it.kind) }, { it.value }))
                .firstOrNull()
                ?.value
        if (!localized.isNullOrBlank()) return localized

        if (languageCode.equals("en", ignoreCase = true)) {
            entry.settlement.englishName?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return entry.settlement.name
    }

    private fun nameKindPriority(kind: String): Int =
        when (kind) {
            "primary" -> 0
            "localized" -> 1
            "official" -> 2
            else -> 3
        }
}
