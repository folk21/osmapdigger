package com.permieware.osmapdigger.preferences

import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Current dataset-scoped search context persisted by a platform-owned store. */
data class UserPreferences(
    val datasetId: String,
    val centerSettlementId: String? = null,
    val centerSettlementName: String? = null,
    val radiusKm: Double? = null,
    val conditions: List<SearchCondition> = emptyList(),
)

/** Platform persistence boundary for the current restorable user search context. */
interface UserPreferencesRepository {
    suspend fun load(): UserPreferences?

    suspend fun save(preferences: UserPreferences)
}

/** Search state returned after validating persisted preferences against an opened dataset. */
data class RestoredSearchContext(
    val center: Settlement?,
    val radiusKm: Double?,
    val conditions: List<SearchCondition>,
)

/**
 * Validate persisted state against the currently opened dataset and metric catalog.
 *
 * Dataset identity and stable IDs are authoritative. Display names are never used as
 * fallback identity, and removed metrics are ignored deterministically.
 */
object UserPreferencesRestorer {
    suspend fun restore(
        preferences: UserPreferences?,
        datasetId: String,
        definitions: List<MetricDefinition>,
        resolveSettlement: suspend (String) -> Settlement?,
    ): RestoredSearchContext? {
        if (preferences == null || preferences.datasetId != datasetId) {
            return null
        }

        val validMetricIds = definitions.asSequence().map { it.id }.toHashSet()
        val restoredConditions =
            preferences.conditions
                .asSequence()
                .filter { it.metricId in validMetricIds }
                .distinctBy { it.metricId }
                .toList()

        val centerId = preferences.centerSettlementId
        if (centerId == null) {
            return RestoredSearchContext(
                center = null,
                radiusKm = null,
                conditions = restoredConditions,
            )
        }

        val center = resolveSettlement(centerId)
        val radius =
            preferences.radiusKm
                ?.takeIf { it.isFinite() && it > 0.0 }

        return RestoredSearchContext(
            center = center,
            radiusKm = if (center != null) radius else null,
            conditions = restoredConditions,
        )
    }
}

/** Versioned JSON representation used inside the platform settings database. */
object SearchConditionPayloadCodec {
    private const val CURRENT_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(conditions: List<SearchCondition>): String =
        json.encodeToString(
            FilterPayload(
                version = CURRENT_VERSION,
                filters =
                    conditions.map {
                        FilterEntry(
                            metricId = it.metricId,
                            minValue = it.minValue,
                            maxValue = it.maxValue,
                        )
                    },
            ),
        )

    /** Return null for malformed or unsupported payloads so callers can use defaults. */
    fun decode(payload: String): List<SearchCondition>? =
        try {
            val decoded = json.decodeFromString<FilterPayload>(payload)
            if (decoded.version != CURRENT_VERSION) {
                null
            } else {
                decoded.filters.map {
                    SearchCondition(
                        metricId = it.metricId,
                        minValue = it.minValue,
                        maxValue = it.maxValue,
                    )
                }
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    @Serializable
    private data class FilterPayload(
        val version: Int,
        val filters: List<FilterEntry>,
    )

    @Serializable
    private data class FilterEntry(
        val metricId: String,
        val minValue: Double? = null,
        val maxValue: Double? = null,
    )
}
