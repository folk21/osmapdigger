package com.permieware.osmapdigger.external

import com.permieware.osmapdigger.domain.Settlement

enum class ExternalSearchProvider(val title: String) {
    GOOGLE("Google"),
    YANDEX("Yandex"),
}

/** Builds browser queries for property discovery without scraping listing sites. */
object PropertySearchLinks {
    fun build(
        provider: ExternalSearchProvider,
        settlement: Settlement,
        site: String? = null,
        terms: String = "property",
    ): String {
        val sitePart = site?.takeIf { it.isNotBlank() }?.let { "site:$it " } ?: ""
        val query = "$sitePart\"${settlement.name}\" $terms"
        val encoded = percentEncode(query)
        return when (provider) {
            ExternalSearchProvider.GOOGLE -> "https://www.google.com/search?q=$encoded"
            ExternalSearchProvider.YANDEX -> "https://yandex.com/search/?text=$encoded"
        }
    }

    internal fun percentEncode(value: String): String =
        buildString {
            value.encodeToByteArray().forEach { byte ->
                val unsigned = byte.toInt() and 0xFF
                val char = unsigned.toChar()
                if (
                    char in 'a'..'z' ||
                    char in 'A'..'Z' ||
                    char in '0'..'9' ||
                    char in "-_.~"
                ) {
                    append(char)
                } else {
                    append('%')
                    append(unsigned.toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
}
