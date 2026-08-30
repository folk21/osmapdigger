package com.permieware.osmapdigger.workspace

import com.permieware.osmapdigger.analysis.MetricPreference
import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.analysis.SettlementAnalysisDiagnostics
import com.permieware.osmapdigger.analysis.SettlementAnalysisRequest
import com.permieware.osmapdigger.analysis.SettlementAnalysisService
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricPreferenceDefault
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.SearchRequest
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference
import com.permieware.osmapdigger.preferences.MetricPreferenceOverride
import com.permieware.osmapdigger.preferences.MetricPreferenceOverrideResolver
import com.permieware.osmapdigger.preferences.UserPreferences
import com.permieware.osmapdigger.preferences.UserPreferencesRepository
import com.permieware.osmapdigger.preferences.UserPreferencesRestorer
import com.permieware.osmapdigger.dataset.GeoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Immutable shared state for one opened dataset's analysis workflow. */
data class AnalysisWorkspaceState(
    val initialized: Boolean = false,
    val datasetInfo: DatasetInfo? = null,
    val definitions: List<MetricDefinition> = emptyList(),
    val conditions: List<SearchCondition> = emptyList(),
    val preferenceDefaults: List<MetricPreferenceDefault> = emptyList(),
    val preferenceOverrides: List<MetricPreferenceOverride> = emptyList(),
    val effectivePreferences: List<EffectiveMetricPreference> = emptyList(),
    val center: Settlement? = null,
    val radiusKm: Double? = null,
    val rankedResults: List<ScoredSettlement> = emptyList(),
    val analyzing: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Coordinate shared analysis input, persistence, and automatically refreshed ranked results.
 *
 * The caller owns [scope]. Rapid input changes cancel the pending debounce and supersede stale
 * analysis generations; a late result from older work is never allowed to overwrite newer state.
 */
class AnalysisWorkspaceController(
    private val repository: GeoRepository,
    private val userPreferences: UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val onAnalysisDiagnostics: (SettlementAnalysisDiagnostics) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(AnalysisWorkspaceState())
    val state: StateFlow<AnalysisWorkspaceState> = mutableState.asStateFlow()

    private val analysisService = SettlementAnalysisService(repository)
    private var analysisJob: Job? = null
    private var analysisGeneration: Long = 0
    private var persistenceGeneration: Long = 0
    private val persistenceMutex = Mutex()

    suspend fun initialize() {
        analysisJob?.cancel()
        analysisGeneration += 1
        mutableState.value = AnalysisWorkspaceState()

        try {
            val info = repository.datasetInfo()
            val definitions = repository.metricDefinitions()
            val preferenceDefaults = repository.preferenceDefaults()
            val saved = runCatching { userPreferences.load() }.getOrNull()
            val restoredSearch =
                UserPreferencesRestorer.restore(
                    preferences = saved,
                    datasetId = info.id,
                    definitions = definitions,
                    resolveSettlement = { settlementId ->
                        runCatching { repository.details(settlementId).settlement }.getOrNull()
                    },
                )
            val defaultConditions =
                definitions
                    .filter { it.defaultEnabled }
                    .map { SearchCondition(metricId = it.id) }
            val validPreferenceMetricIds = preferenceDefaults.mapTo(hashSetOf()) { it.metricId }
            val overrides =
                if (restoredSearch != null) {
                    saved?.preferenceOverrides.orEmpty().filter { it.metricId in validPreferenceMetricIds }
                } else {
                    emptyList()
                }
            val effectivePreferences =
                MetricPreferenceOverrideResolver.resolve(preferenceDefaults, overrides)

            mutableState.value =
                AnalysisWorkspaceState(
                    initialized = true,
                    datasetInfo = info,
                    definitions = definitions,
                    conditions = restoredSearch?.conditions ?: defaultConditions,
                    preferenceDefaults = preferenceDefaults,
                    preferenceOverrides = overrides,
                    effectivePreferences = effectivePreferences,
                    center = restoredSearch?.center,
                    radiusKm = restoredSearch?.radiusKm,
                )
            persistCurrentState()
            scheduleAnalysis(immediate = true)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            mutableState.value =
                mutableState.value.copy(
                    initialized = false,
                    analyzing = false,
                    errorMessage = error.message ?: error.toString(),
                )
        }
    }

    fun updateConditions(conditions: List<SearchCondition>) {
        require(conditions.map { it.metricId }.distinct().size == conditions.size) {
            "Search conditions must contain unique metric IDs"
        }
        mutableState.value = mutableState.value.copy(conditions = conditions, errorMessage = null)
        persistAndSchedule()
    }

    fun updateCenter(center: Settlement?) {
        val current = mutableState.value
        mutableState.value =
            current.copy(
                center = center,
                radiusKm = if (center == null) null else current.radiusKm,
                errorMessage = null,
            )
        persistAndSchedule()
    }

    fun updateRadiusKm(radiusKm: Double?) {
        require(radiusKm == null || radiusKm.isFinite() && radiusKm > 0.0) {
            "Radius must be null or a positive finite value"
        }
        require(radiusKm == null || mutableState.value.center != null) {
            "Radius requires a selected center settlement"
        }
        mutableState.value = mutableState.value.copy(radiusKm = radiusKm, errorMessage = null)
        persistAndSchedule()
    }

    fun updatePreferenceOverrides(overrides: List<MetricPreferenceOverride>) {
        val current = mutableState.value
        require(overrides.map { it.metricId }.distinct().size == overrides.size) {
            "Preference overrides must contain unique metric IDs"
        }
        val validMetricIds = current.preferenceDefaults.mapTo(hashSetOf()) { it.metricId }
        require(overrides.all { it.metricId in validMetricIds }) {
            "Preference overrides must reference dataset preference defaults"
        }
        val resolved = MetricPreferenceOverrideResolver.resolve(current.preferenceDefaults, overrides)
        mutableState.value =
            mutableState.value.copy(
                preferenceOverrides = overrides,
                effectivePreferences = resolved,
                errorMessage = null,
            )
        persistAndSchedule()
    }

    fun updatePreferenceEnabled(metricId: String, enabled: Boolean) {
        if (mutableState.value.effectivePreferences.firstOrNull { it.metricId == metricId }?.enabled == enabled) {
            return
        }
        updatePreferenceOverride(metricId) { default, current ->
            current.copy(enabled = enabled.takeUnless { it == default.defaultEnabled })
        }
    }

    fun updatePreferenceWeight(metricId: String, weight: Int) {
        require(weight in MetricPreference.WEIGHT_RANGE) {
            "Preference weight must be in ${MetricPreference.WEIGHT_RANGE}"
        }
        if (
            mutableState.value.effectivePreferences
                .firstOrNull { it.metricId == metricId }
                ?.preference
                ?.weight == weight
        ) {
            return
        }
        updatePreferenceOverride(metricId) { default, current ->
            current.copy(weight = weight.takeUnless { it == default.weight })
        }
    }

    fun updatePreferenceThresholds(
        metricId: String,
        targetValue: Double,
        limitValue: Double,
    ) {
        val default = requirePreferenceDefault(metricId)
        val effective = mutableState.value.effectivePreferences.first { it.metricId == metricId }.preference
        if (effective.targetValue == targetValue && effective.limitValue == limitValue) return
        MetricPreference(
            metricId = metricId,
            direction = default.direction,
            targetValue = targetValue,
            limitValue = limitValue,
            weight =
                effective.weight,
        )
        updatePreferenceOverride(metricId) { persistedDefault, current ->
            current.copy(
                targetValue = targetValue.takeUnless { it == persistedDefault.targetValue },
                limitValue = limitValue.takeUnless { it == persistedDefault.limitValue },
            )
        }
    }

    fun resetPreference(metricId: String) {
        requirePreferenceDefault(metricId)
        if (mutableState.value.preferenceOverrides.none { it.metricId == metricId }) return
        updatePreferenceOverrides(
            mutableState.value.preferenceOverrides.filterNot { it.metricId == metricId },
        )
    }

    fun refreshNow() {
        scheduleAnalysis(immediate = true, force = true)
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(errorMessage = null)
    }

    private fun updatePreferenceOverride(
        metricId: String,
        transform: (MetricPreferenceDefault, MetricPreferenceOverride) -> MetricPreferenceOverride,
    ) {
        val snapshot = mutableState.value
        val default = requirePreferenceDefault(metricId)
        val current =
            snapshot.preferenceOverrides.firstOrNull { it.metricId == metricId }
                ?: MetricPreferenceOverride(metricId = metricId)
        val transformed = transform(default, current)
        val updatedById = snapshot.preferenceOverrides.associateByTo(linkedMapOf()) { it.metricId }
        if (transformed.isEmptyOverride()) {
            updatedById.remove(metricId)
        } else {
            updatedById[metricId] = transformed
        }
        val ordered = snapshot.preferenceDefaults.mapNotNull { updatedById[it.metricId] }
        updatePreferenceOverrides(ordered)
    }

    private fun requirePreferenceDefault(metricId: String): MetricPreferenceDefault =
        mutableState.value.preferenceDefaults.firstOrNull { it.metricId == metricId }
            ?: throw IllegalArgumentException(
                "Preference '$metricId' is not defined by the current dataset",
            )

    private fun MetricPreferenceOverride.isEmptyOverride(): Boolean =
        enabled == null && targetValue == null && limitValue == null && weight == null

    private fun persistAndSchedule() {
        persistCurrentState()
        scheduleAnalysis()
    }

    private fun persistCurrentState() {
        val snapshot = mutableState.value
        val info = snapshot.datasetInfo ?: return
        if (!snapshot.initialized) return
        val generation = ++persistenceGeneration

        scope.launch {
            try {
                persistenceMutex.withLock {
                    if (generation != persistenceGeneration) return@withLock
                    userPreferences.save(
                        UserPreferences(
                            datasetId = info.id,
                            centerSettlementId = snapshot.center?.id,
                            centerSettlementName = snapshot.center?.name,
                            radiusKm = snapshot.radiusKm,
                            conditions = snapshot.conditions,
                            preferenceOverrides = snapshot.preferenceOverrides,
                        ),
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == persistenceGeneration) {
                    mutableState.value =
                        mutableState.value.copy(
                            errorMessage = "Could not save user preferences: ${error.message ?: error}",
                        )
                }
            }
        }
    }

    private fun scheduleAnalysis(
        immediate: Boolean = false,
        force: Boolean = false,
    ) {
        val snapshot = mutableState.value
        if (!snapshot.initialized) return

        val hasEnabledPreferences = snapshot.effectivePreferences.any { it.enabled }
        val hasEffectiveHardConstraints = snapshot.conditions.any { it.isEffective }
        val hasRadiusConstraint = snapshot.center != null && snapshot.radiusKm != null
        if (!force && !hasEnabledPreferences && !hasEffectiveHardConstraints && !hasRadiusConstraint) {
            analysisJob?.cancel()
            analysisGeneration += 1
            mutableState.value =
                snapshot.copy(
                    rankedResults = emptyList(),
                    analyzing = false,
                    errorMessage = null,
                )
            return
        }

        val generation = ++analysisGeneration
        analysisJob?.cancel()
        analysisJob =
            scope.launch {
                if (!immediate && debounceMillis > 0) {
                    delay(debounceMillis)
                }
                if (generation != analysisGeneration) return@launch

                mutableState.value = mutableState.value.copy(analyzing = true, errorMessage = null)
                try {
                    val enabledPreferences =
                        snapshot.effectivePreferences
                            .asSequence()
                            .filter { it.enabled }
                            .map { it.preference }
                            .toList()
                    val outcome =
                        analysisService.analyzeWithDiagnostics(
                            SettlementAnalysisRequest(
                                search =
                                    SearchRequest(
                                        center = snapshot.center,
                                        radiusKm = snapshot.radiusKm,
                                        conditions = snapshot.conditions,
                                    ),
                                preferences = enabledPreferences,
                            ),
                        )
                    if (generation == analysisGeneration) {
                        runCatching { onAnalysisDiagnostics(outcome.diagnostics) }
                        mutableState.value =
                            mutableState.value.copy(
                                rankedResults = outcome.results,
                                analyzing = false,
                                errorMessage = null,
                            )
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    if (generation == analysisGeneration) {
                        mutableState.value =
                            mutableState.value.copy(
                                analyzing = false,
                                errorMessage = error.message ?: error.toString(),
                            )
                    }
                }
            }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS: Long = 250
    }
}
