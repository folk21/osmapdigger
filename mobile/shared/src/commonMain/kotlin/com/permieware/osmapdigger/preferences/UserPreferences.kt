package com.permieware.osmapdigger.preferences

import com.permieware.osmapdigger.analysis.MetricPreference
import com.permieware.osmapdigger.analysis.SettlementCandidateScope
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricPreferenceDefault
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.settings.ApplicationSettingsSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Current dataset-scoped analysis context persisted by a platform-owned store. */
data class UserPreferences(
    val datasetId: String,
    val centerSettlementId: String? = null,
    val centerSettlementName: String? = null,
    val radiusKm: Double? = null,
    val conditions: List<SearchCondition> = emptyList(),
    val preferenceOverrides: List<MetricPreferenceOverride> = emptyList(),
    val candidateScope: SettlementCandidateScope = SettlementCandidateScope.Dataset,
)

/** User-owned changes layered over one dataset-provided preference default. */
data class MetricPreferenceOverride(
    val metricId: String,
    val enabled: Boolean? = null,
    val targetValue: Double? = null,
    val limitValue: Double? = null,
    val weight: Int? = null,
) {
    init {
        require(metricId.isNotBlank()) { "Preference override metric ID must not be blank" }
        require(targetValue == null || targetValue.isFinite()) {
            "Preference override target must be finite when provided"
        }
        require(limitValue == null || limitValue.isFinite()) {
            "Preference override limit must be finite when provided"
        }
        require(weight == null || weight in MetricPreference.WEIGHT_RANGE) {
            "Preference override weight must be in ${MetricPreference.WEIGHT_RANGE} when provided"
        }
    }
}

/** Effective preference state after applying user overrides to dataset defaults. */
data class EffectiveMetricPreference(
    val metricId: String,
    val enabled: Boolean,
    val preference: MetricPreference,
)

/** Platform persistence boundary for the current restorable user analysis context. */
interface UserPreferencesRepository {
    suspend fun load(): UserPreferences?

    suspend fun save(preferences: UserPreferences)
}

/** Restored analysis state after validating persisted preferences against an opened dataset. */
data class RestoredSearchContext(
    val center: Settlement?,
    val radiusKm: Double?,
    val conditions: List<SearchCondition>,
    val candidateScope: SettlementCandidateScope,
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
        availableSettlementIds: Set<String> = emptySet(),
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

        val restoredCandidateScope =
            when (val savedScope = preferences.candidateScope) {
                SettlementCandidateScope.Dataset -> SettlementCandidateScope.Dataset
                is SettlementCandidateScope.Imported ->
                    SettlementCandidateScope.Imported(
                        savedScope.settlementIds.filter { it in availableSettlementIds },
                    )
            }

        val centerId = preferences.centerSettlementId
        if (centerId == null) {
            return RestoredSearchContext(
                center = null,
                radiusKm = null,
                conditions = restoredConditions,
                candidateScope = restoredCandidateScope,
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
            candidateScope = restoredCandidateScope,
        )
    }
}

/** Merge dataset defaults with dataset-scoped user overrides by stable metric ID. */
object MetricPreferenceOverrideResolver {
    /** Resolve saved overrides only when they belong to the currently opened dataset. */
    fun restore(
        preferences: UserPreferences?,
        datasetId: String,
        defaults: List<MetricPreferenceDefault>,
    ): List<EffectiveMetricPreference>? {
        if (preferences == null || preferences.datasetId != datasetId) {
            return null
        }
        return resolve(defaults, preferences.preferenceOverrides)
    }

    fun resolve(
        defaults: List<MetricPreferenceDefault>,
        overrides: List<MetricPreferenceOverride>,
    ): List<EffectiveMetricPreference> {
        require(overrides.map { it.metricId }.distinct().size == overrides.size) {
            "Preference overrides must contain unique metric IDs"
        }
        val overrideByMetricId = overrides.associateBy { it.metricId }
        return defaults.map { default ->
            val override = overrideByMetricId[default.metricId]
            val preference =
                MetricPreference(
                    metricId = default.metricId,
                    direction = default.direction,
                    targetValue = override?.targetValue ?: default.targetValue,
                    limitValue = override?.limitValue ?: default.limitValue,
                    weight = override?.weight ?: default.weight,
                )
            EffectiveMetricPreference(
                metricId = default.metricId,
                enabled = override?.enabled ?: default.defaultEnabled,
                preference = preference,
            )
        }
    }
}

/** Versioned JSON representation for the dataset-wide or reviewed imported candidate scope. */
object SettlementCandidateScopePayloadCodec {
    private const val CURRENT_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(scope: SettlementCandidateScope): String =
        when (scope) {
            SettlementCandidateScope.Dataset -> ApplicationSettingsSchema.DEFAULT_CANDIDATE_SCOPE_PAYLOAD
            is SettlementCandidateScope.Imported ->
                json.encodeToString(
                    CandidateScopePayload(
                        version = CURRENT_VERSION,
                        source = SOURCE_IMPORTED,
                        settlementIds = scope.settlementIds,
                    ),
                )
        }

    /** Return null for malformed, unsupported, or semantically invalid payloads. */
    fun decode(payload: String): SettlementCandidateScope? =
        try {
            val decoded = json.decodeFromString<CandidateScopePayload>(payload)
            if (decoded.version != CURRENT_VERSION) {
                null
            } else {
                when (decoded.source) {
                    SOURCE_DATASET -> SettlementCandidateScope.Dataset
                    SOURCE_IMPORTED -> SettlementCandidateScope.Imported(decoded.settlementIds)
                    else -> null
                }
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    @Serializable
    private data class CandidateScopePayload(
        val version: Int,
        val source: String,
        val settlementIds: List<String> = emptyList(),
    )

    private const val SOURCE_DATASET = "dataset"
    private const val SOURCE_IMPORTED = "imported"
}

/** Versioned JSON representation used for dynamic hard-filter state. */
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

    /** Return null for malformed or unsupported payloads so the storage boundary can classify the failure. */
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

/** Versioned JSON representation for dataset-scoped preference overrides. */
object MetricPreferenceOverridePayloadCodec {
    private const val CURRENT_VERSION = 1

    const val EMPTY_PAYLOAD: String = "{\"version\":1,\"preferences\":[]}"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(overrides: List<MetricPreferenceOverride>): String =
        json.encodeToString(
            PreferencePayload(
                version = CURRENT_VERSION,
                preferences =
                    overrides.map {
                        PreferenceEntry(
                            metricId = it.metricId,
                            enabled = it.enabled,
                            targetValue = it.targetValue,
                            limitValue = it.limitValue,
                            weight = it.weight,
                        )
                    },
            ),
        )

    /** Return null for malformed, unsupported, or semantically invalid payloads. */
    fun decode(payload: String): List<MetricPreferenceOverride>? =
        try {
            val decoded = json.decodeFromString<PreferencePayload>(payload)
            if (decoded.version != CURRENT_VERSION) {
                null
            } else {
                decoded.preferences.map {
                    MetricPreferenceOverride(
                        metricId = it.metricId,
                        enabled = it.enabled,
                        targetValue = it.targetValue,
                        limitValue = it.limitValue,
                        weight = it.weight,
                    )
                }
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    @Serializable
    private data class PreferencePayload(
        val version: Int,
        val preferences: List<PreferenceEntry>,
    )

    @Serializable
    private data class PreferenceEntry(
        val metricId: String,
        val enabled: Boolean? = null,
        val targetValue: Double? = null,
        val limitValue: Double? = null,
        val weight: Int? = null,
    )
}
