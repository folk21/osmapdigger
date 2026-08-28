package com.permieware.osmapdigger.analysis

import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricPreferenceDefault
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementAnalysisCandidate
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.domain.SettlementSearchEntry
import com.permieware.osmapdigger.preferences.MetricPreferenceOverride
import com.permieware.osmapdigger.preferences.UserPreferences
import com.permieware.osmapdigger.preferences.UserPreferencesRepository
import com.permieware.osmapdigger.runtime.GeoRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisWorkspaceControllerTest {
    @Test
    fun initializeRestoresSearchAndOverridesThenRanksImmediately() = runTest {
        val center = settlement("center", "Center")
        val repository =
            FakeRepository(
                candidates = listOf(candidate("best", "Best", forestDistance = 1.0)),
                detailsById = mapOf(center.id to SettlementDetails(center, emptyList())),
            )
        val preferences =
            FakePreferencesRepository(
                UserPreferences(
                    datasetId = "dataset",
                    centerSettlementId = center.id,
                    radiusKm = 20.0,
                    conditions = listOf(SearchCondition("hard", maxValue = 3.0)),
                    preferenceOverrides =
                        listOf(MetricPreferenceOverride("forest.distance_km", weight = 10)),
                ),
            )
        val controller = AnalysisWorkspaceController(repository, preferences, this, debounceMillis = 250)

        controller.initialize()
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state.initialized)
        assertEquals(center, state.center)
        assertEquals(20.0, state.radiusKm)
        assertEquals(listOf(SearchCondition("hard", maxValue = 3.0)), state.conditions)
        assertEquals(10, state.effectivePreferences.single().preference.weight)
        assertEquals(listOf("best"), state.rankedResults.map { it.settlement.id })
        assertEquals(setOf("forest.distance_km"), repository.lastScoringMetricIds)
        assertFalse(state.analyzing)
        assertNull(state.errorMessage)
    }

    @Test
    fun rapidInputChangesAreDebouncedAndOnlyLatestConditionsAreAnalyzed() = runTest {
        val repository = FakeRepository(candidates = emptyList())
        val controller =
            AnalysisWorkspaceController(
                repository = repository,
                userPreferences = FakePreferencesRepository(null),
                scope = this,
                debounceMillis = 250,
            )

        controller.initialize()
        advanceUntilIdle()
        repository.analysisCalls = 0

        controller.updateConditions(listOf(SearchCondition("hard", maxValue = 5.0)))
        advanceTimeBy(100)
        controller.updateConditions(listOf(SearchCondition("hard", maxValue = 2.0)))
        advanceTimeBy(249)
        assertEquals(0, repository.analysisCalls)

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(1, repository.analysisCalls)
        assertEquals(listOf(SearchCondition("hard", maxValue = 2.0)), repository.lastConditions)
    }

    @Test
    fun preferenceOverrideChangePersistsAndRecalculatesWithEnabledPreferencesOnly() = runTest {
        val repository = FakeRepository(candidates = emptyList())
        val preferences = FakePreferencesRepository(null)
        val controller = AnalysisWorkspaceController(repository, preferences, this, debounceMillis = 0)

        controller.initialize()
        advanceUntilIdle()
        repository.analysisCalls = 0

        controller.updatePreferenceOverrides(
            listOf(
                MetricPreferenceOverride(
                    metricId = "forest.distance_km",
                    enabled = false,
                    weight = 9,
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(1, repository.analysisCalls)
        assertEquals(emptySet(), repository.lastScoringMetricIds)
        assertEquals(9, controller.state.value.effectivePreferences.single().preference.weight)
        assertFalse(controller.state.value.effectivePreferences.single().enabled)
        assertEquals(9, preferences.saved.last().preferenceOverrides.single().weight)
    }



    @Test
    fun staleAnalysisCompletionCannotOverwriteNewerResults() = runTest {
        val repository = FakeRepository(candidates = listOf(candidate("initial", "Initial", 5.0)))
        val controller =
            AnalysisWorkspaceController(
                repository = repository,
                userPreferences = FakePreferencesRepository(null),
                scope = this,
                debounceMillis = 250,
            )

        controller.initialize()
        advanceUntilIdle()
        repository.analysisHandler = { conditions ->
            when (conditions.singleOrNull()?.maxValue) {
                5.0 -> {
                    withContext(NonCancellable) { delay(1_000) }
                    listOf(candidate("stale", "Stale", 5.0))
                }
                2.0 -> listOf(candidate("new", "New", 1.0))
                else -> emptyList()
            }
        }

        controller.updateConditions(listOf(SearchCondition("hard", maxValue = 5.0)))
        advanceTimeBy(250)
        controller.updateConditions(listOf(SearchCondition("hard", maxValue = 2.0)))
        advanceTimeBy(250)
        assertEquals(listOf("new"), controller.state.value.rankedResults.map { it.settlement.id })

        advanceTimeBy(750)
        advanceUntilIdle()

        assertEquals(listOf("new"), controller.state.value.rankedResults.map { it.settlement.id })
    }

    @Test
    fun legacyDatasetWithoutPreferencesDoesNotAutoScanUntilManualRefresh() = runTest {
        val repository = FakeRepository(candidates = emptyList(), preferenceDefaults = emptyList())
        val controller =
            AnalysisWorkspaceController(
                repository = repository,
                userPreferences = FakePreferencesRepository(null),
                scope = this,
                debounceMillis = 0,
            )

        controller.initialize()
        advanceUntilIdle()

        assertEquals(0, repository.analysisCalls)
        assertTrue(controller.state.value.rankedResults.isEmpty())

        controller.refreshNow()
        advanceUntilIdle()

        assertEquals(1, repository.analysisCalls)
    }

    @Test
    fun clearingCenterAlsoClearsRadiusAndPersistsThatInvariant() = runTest {
        val repository = FakeRepository(candidates = emptyList())
        val preferences = FakePreferencesRepository(null)
        val controller = AnalysisWorkspaceController(repository, preferences, this, debounceMillis = 0)
        val center = settlement("center", "Center")

        controller.initialize()
        advanceUntilIdle()
        controller.updateCenter(center)
        controller.updateRadiusKm(15.0)
        advanceUntilIdle()
        controller.updateCenter(null)
        advanceUntilIdle()

        assertNull(controller.state.value.center)
        assertNull(controller.state.value.radiusKm)
        assertNull(preferences.saved.last().centerSettlementId)
        assertNull(preferences.saved.last().radiusKm)
    }

    private fun candidate(id: String, name: String, forestDistance: Double) =
        SettlementAnalysisCandidate(
            settlement = settlement(id, name),
            metricValues = mapOf("forest.distance_km" to forestDistance),
        )

    private fun settlement(id: String, name: String) =
        Settlement(
            id = id,
            name = name,
            localName = null,
            englishName = null,
            placeType = "village",
            population = null,
            location = GeoPoint(0.0, 0.0),
        )

    private class FakePreferencesRepository(initial: UserPreferences?) : UserPreferencesRepository {
        private var current = initial
        val saved = mutableListOf<UserPreferences>()

        override suspend fun load(): UserPreferences? = current

        override suspend fun save(preferences: UserPreferences) {
            current = preferences
            saved += preferences
        }
    }

    private class FakeRepository(
        private val candidates: List<SettlementAnalysisCandidate>,
        private val detailsById: Map<String, SettlementDetails> = emptyMap(),
        private val preferenceDefaults: List<MetricPreferenceDefault> =
            listOf(
                MetricPreferenceDefault(
                    metricId = "forest.distance_km",
                    direction = PreferredDirection.LOWER,
                    targetValue = 2.0,
                    limitValue = 10.0,
                    weight = 5,
                    defaultEnabled = true,
                ),
            ),
    ) : GeoRepository {
        var analysisCalls: Int = 0
        var lastConditions: List<SearchCondition> = emptyList()
        var lastScoringMetricIds: Set<String> = emptySet()
        var analysisHandler: (suspend (List<SearchCondition>) -> List<SettlementAnalysisCandidate>)? = null

        override suspend fun datasetInfo() =
            DatasetInfo("dataset", "Dataset", null, GeoPoint(0.0, 0.0), 8.0, false, null, "property")

        override suspend fun metricDefinitions(): List<MetricDefinition> =
            listOf(
                MetricDefinition(
                    id = "hard",
                    categoryId = "hard",
                    group = "Test",
                    title = "Hard",
                    description = "Hard",
                    unit = "km",
                    measureType = "distance",
                    preferredDirection = PreferredDirection.LOWER,
                    defaultEnabled = false,
                    sortOrder = 0,
                ),
                MetricDefinition(
                    id = "forest.distance_km",
                    categoryId = "forest",
                    group = "Nature",
                    title = "Forest",
                    description = "Forest",
                    unit = "km",
                    measureType = "distance",
                    preferredDirection = PreferredDirection.LOWER,
                    defaultEnabled = false,
                    sortOrder = 1,
                ),
            )

        override suspend fun preferenceDefaults(): List<MetricPreferenceDefault> = preferenceDefaults

        override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> = emptyList()

        override suspend fun searchCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            limit: Int,
        ): List<Settlement> = error("Legacy search must not be used")

        override suspend fun analysisCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            scoringMetricIds: Set<String>,
        ): List<SettlementAnalysisCandidate> {
            analysisCalls += 1
            lastConditions = conditions
            lastScoringMetricIds = scoringMetricIds
            return analysisHandler?.invoke(conditions) ?: candidates
        }

        override suspend fun details(settlementId: String): SettlementDetails =
            detailsById[settlementId] ?: error("Unknown settlement: $settlementId")
    }
}
