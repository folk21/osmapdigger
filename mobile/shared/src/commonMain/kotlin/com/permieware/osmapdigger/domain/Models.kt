package com.permieware.osmapdigger.domain

/** Geographic coordinate in WGS84. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** Metadata describing one installed OsmapDigger dataset. */
data class DatasetInfo(
    val id: String,
    val displayName: String,
    val countryCode: String?,
    val center: GeoPoint,
    val initialZoom: Double,
    val hasMap: Boolean,
    val propertySearchSite: String?,
    val propertySearchTerms: String,
)

/** Direction that normally represents a more desirable value. */
enum class PreferredDirection {
    LOWER,
    HIGHER,
    NEUTRAL,
}

/** One numeric metric exposed by an installed dataset. */
data class MetricDefinition(
    val id: String,
    val categoryId: String,
    val group: String,
    val title: String,
    val description: String,
    val unit: String,
    val measureType: String,
    val preferredDirection: PreferredDirection,
    val defaultEnabled: Boolean,
    val sortOrder: Int,
)

/** Searchable settlement record. */
data class Settlement(
    val id: String,
    val name: String,
    val localName: String?,
    val englishName: String?,
    val placeType: String?,
    val population: Long?,
    val location: GeoPoint,
)

/** One searchable name associated with a canonical settlement. */
data class SettlementName(
    val value: String,
    val normalizedValue: String,
    val language: String? = null,
    val kind: String = "alternate",
)

/** Search-index row containing one settlement and all persisted/fallback aliases. */
data class SettlementSearchEntry(
    val settlement: Settlement,
    val names: List<SettlementName>,
)

/** Quality of the best alias match for a settlement query. */
enum class SettlementMatchKind {
    EXACT,
    PREFIX,
    SUBSTRING,
    FUZZY,
}

/** Ranked settlement lookup result used by the center picker. */
data class SettlementSearchMatch(
    val settlement: Settlement,
    val matchedName: String,
    val aliases: List<String>,
    val kind: SettlementMatchKind,
)

/** Optional min/max constraint for one metric. */
data class SearchCondition(
    val metricId: String,
    val minValue: Double? = null,
    val maxValue: Double? = null,
) {
    val isEffective: Boolean
        get() = minValue != null || maxValue != null
}

/** Full deterministic settlement search. */
data class SearchRequest(
    val center: Settlement? = null,
    val radiusKm: Double? = null,
    val conditions: List<SearchCondition> = emptyList(),
    val limit: Int = 500,
)

/** One metric value attached to a settlement. */
data class MetricValue(
    val definition: MetricDefinition,
    val value: Double,
)

/** Detailed settlement result used by the information panel. */
data class SettlementDetails(
    val settlement: Settlement,
    val metrics: List<MetricValue>,
)
