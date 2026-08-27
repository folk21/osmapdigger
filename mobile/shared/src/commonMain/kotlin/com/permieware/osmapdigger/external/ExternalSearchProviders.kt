package com.permieware.osmapdigger.external

import com.permieware.osmapdigger.domain.Settlement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Immutable application-owned definition of one external settlement search action. */
data class ExternalSearchProvider(
    val id: String,
    val title: String,
    val countryCode: String?,
    val urlTemplate: String,
    val priority: Int,
)

/** Loads enabled external search actions applicable to a dataset country. */
fun interface ExternalSearchProviderRepository {
    suspend fun providersFor(countryCode: String?): List<ExternalSearchProvider>
}

/** Parses the packaged provider seed configuration before it is inserted into settings SQLite. */
object ExternalSearchProviderCatalog {
    const val RESOURCE_NAME = "external-search-providers.json"

    fun decode(payload: String): List<ExternalSearchProvider> {
        val root = Json.parseToJsonElement(payload).jsonObject
        val providers =
            requireNotNull(root["providers"]) { "External search provider seed has no providers array" }
                .jsonArray
                .map { element ->
                    val item = element.jsonObject
                    ExternalSearchProvider(
                        id = requireNotNull(item["id"]).jsonPrimitive.content,
                        title = requireNotNull(item["title"]).jsonPrimitive.content,
                        countryCode = item["countryCode"]?.jsonPrimitive?.contentOrNull,
                        urlTemplate = requireNotNull(item["urlTemplate"]).jsonPrimitive.content,
                        priority = requireNotNull(item["priority"]).jsonPrimitive.int,
                    )
                }

        require(providers.isNotEmpty()) { "External search provider seed must not be empty" }
        require(providers.map { it.id to it.countryCode }.distinct().size == providers.size) {
            "External search provider IDs must be unique within each country scope"
        }
        providers.forEach { provider ->
            require(provider.id.isNotBlank()) { "External search provider ID must not be blank" }
            require(provider.title.isNotBlank()) { "External search provider title must not be blank" }
            require(provider.urlTemplate.startsWith("https://")) {
                "External search provider '${provider.id}' must use an HTTPS URL template"
            }
            require("{query}" in provider.urlTemplate || "{settlement}" in provider.urlTemplate) {
                "External search provider '${provider.id}' URL template has no query placeholder"
            }
        }
        return providers
    }
}

/** Expands a persisted provider template into an explicit browser URL. */
object ExternalSearchUrlBuilder {
    fun build(
        provider: ExternalSearchProvider,
        settlement: Settlement,
        terms: String = "property",
    ): String {
        val normalizedTerms = terms.trim()
        val rawQuery =
            buildString {
                append('"')
                append(settlement.name)
                append('"')
                if (normalizedTerms.isNotBlank()) {
                    append(' ')
                    append(normalizedTerms)
                }
            }

        return provider.urlTemplate
            .replace("{query}", percentEncode(rawQuery))
            .replace("{settlement}", percentEncode(settlement.name))
            .replace("{terms}", percentEncode(normalizedTerms))
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
