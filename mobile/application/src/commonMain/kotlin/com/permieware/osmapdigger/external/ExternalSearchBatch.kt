package com.permieware.osmapdigger.external

/** One explicit browser action representing a bounded batch search for favorite settlement names. */
data class ExternalSearchBatchAction(
    val providerId: String,
    val providerTitle: String,
    val providerCountryCode: String?,
    val url: String,
    val settlementNames: List<String>,
    val partIndex: Int,
    val partCount: Int,
) {
    init {
        require(providerId.isNotBlank()) { "External search batch provider ID must not be blank" }
        require(providerTitle.isNotBlank()) { "External search batch provider title must not be blank" }
        require(url.startsWith("https://")) { "External search batch URL must use HTTPS" }
        require(settlementNames.isNotEmpty()) { "External search batch action must contain settlement names" }
        require(settlementNames.all { it.isNotBlank() }) { "External search batch settlement names must not be blank" }
        require(partCount > 0) { "External search batch part count must be positive" }
        require(partIndex in 1..partCount) { "External search batch part index must be within part count" }
    }
}

/** Builds deterministic, explicit, URL-bounded external-search actions for several settlement names. */
object ExternalSearchBatchBuilder {
    /** Conservative browser/search URL ceiling measured after UTF-8 percent encoding. */
    const val DEFAULT_MAX_URL_LENGTH = 1800

    fun build(
        providers: List<ExternalSearchProvider>,
        settlementNames: List<String>,
        terms: String = "property",
        maxUrlLength: Int = DEFAULT_MAX_URL_LENGTH,
    ): List<ExternalSearchBatchAction> {
        require(maxUrlLength > 0) { "External search maximum URL length must be positive" }
        val names = normalizedNames(settlementNames)
        if (names.isEmpty()) return emptyList()

        return providers.flatMap { provider ->
            buildForProvider(provider, names, terms, maxUrlLength)
        }
    }

    private fun buildForProvider(
        provider: ExternalSearchProvider,
        settlementNames: List<String>,
        terms: String,
        maxUrlLength: Int,
    ): List<ExternalSearchBatchAction> {
        if (!providerSupportsBatchQuery(provider)) return emptyList()

        val chunks = mutableListOf<List<String>>()
        var current = emptyList<String>()

        for (name in settlementNames) {
            val candidate = current + name
            if (buildBatchUrl(provider, candidate, terms).length <= maxUrlLength) {
                current = candidate
                continue
            }

            if (current.isEmpty()) {
                // The provider cannot safely represent the full selection within the requested bound.
                return emptyList()
            }
            chunks += current
            current = listOf(name)
            if (buildBatchUrl(provider, current, terms).length > maxUrlLength) {
                return emptyList()
            }
        }

        if (current.isNotEmpty()) chunks += current
        val partCount = chunks.size
        return chunks.mapIndexed { index, chunk ->
            ExternalSearchBatchAction(
                providerId = provider.id,
                providerTitle = provider.title,
                providerCountryCode = provider.countryCode,
                url = buildBatchUrl(provider, chunk, terms),
                settlementNames = chunk,
                partIndex = index + 1,
                partCount = partCount,
            )
        }
    }

    private fun normalizedNames(names: List<String>): List<String> =
        buildList {
            val seen = linkedSetOf<String>()
            names.forEach { rawName ->
                val name = rawName.trim()
                if (name.isNotEmpty() && seen.add(name)) add(name)
            }
        }

    private fun providerSupportsBatchQuery(provider: ExternalSearchProvider): Boolean =
        "{query}" in provider.urlTemplate && "{settlement}" !in provider.urlTemplate

    private fun buildBatchUrl(
        provider: ExternalSearchProvider,
        settlementNames: List<String>,
        terms: String,
    ): String {
        val normalizedTerms = provider.resolveQueryTerms(terms)
        val alternatives = settlementNames.joinToString(" OR ") { quoteSearchPhrase(it) }
        val rawQuery =
            buildString {
                if (settlementNames.size > 1) append('(')
                append(alternatives)
                if (settlementNames.size > 1) append(')')
                if (normalizedTerms.isNotBlank()) {
                    append(' ')
                    append(normalizedTerms)
                }
            }

        return provider.urlTemplate
            .replace("{query}", percentEncode(rawQuery))
            .replace("{terms}", percentEncode(normalizedTerms))
    }

    private fun quoteSearchPhrase(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\', '"' -> append('\\')
                }
                append(char)
            }
            append('"')
        }
}
