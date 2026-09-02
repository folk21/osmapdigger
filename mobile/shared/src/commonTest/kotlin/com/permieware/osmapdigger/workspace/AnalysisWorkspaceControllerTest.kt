package com.permieware.osmapdigger.workspace

import com.permieware.osmapdigger.analysis.ImportedCandidateList
import com.permieware.osmapdigger.analysis.SettlementAnalysisDiagnostics
import com.permieware.osmapdigger.analysis.SettlementCandidateScope
import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.analysis.SettlementScore
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
import com.permieware.osmapdigger.notebook.FavoriteAnalysisSnapshotDraft
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.notebook.FavoriteSettlementRepository
import com.permieware.osmapdigger.dataset.GeoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisWorkspaceControllerTest {
    @Test
    fun initializeRestoresSearchAndOverridesThenRanksImmediately() = runTest {
        val center = settlement("center", "Center")
        val repository =
            FakeRepository(
                candidates = listOf(candidate("best", "Best", forestDistance = 1.0)),
                detailsById = mapOf(center.id to SettlementDetails(center, emptyList())),
                searchEntries = listOf(SettlementSearchEntry(center, emptyList())),
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
        assertEquals(1, state.rankedCandidatesBySettlementId.getValue("best").rank)
        assertEquals(setOf("forest.distance_km"), repository.lastScoringMetricIds)
        assertFalse(state.analyzing)
        assertNull(state.failure)
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
    fun disablingLastPreferencePersistsOverrideWithoutUnboundedAnalysis() = runTest {
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

        assertEquals(0, repository.analysisCalls)
        assertTrue(controller.state.value.rankedResults.isEmpty())
        assertEquals(9, controller.state.value.effectivePreferences.single().preference.weight)
        assertFalse(controller.state.value.effectivePreferences.single().enabled)
        assertEquals(9, preferences.saved.last().preferenceOverrides.single().weight)
    }




    @Test
    fun preferenceEditorMethodsPersistSparseOverridesAndResetToDatasetDefaults() = runTest {
        val repository = FakeRepository(candidates = emptyList())
        val preferences = FakePreferencesRepository(null)
        val controller = AnalysisWorkspaceController(repository, preferences, this, debounceMillis = 0)

        controller.initialize()
        advanceUntilIdle()

        controller.updatePreferenceEnabled("forest.distance_km", false)
        controller.updatePreferenceWeight("forest.distance_km", 9)
        controller.updatePreferenceThresholds("forest.distance_km", targetValue = 1.0, limitValue = 12.0)
        advanceUntilIdle()

        val effective = controller.state.value.effectivePreferences.single()
        val override = controller.state.value.preferenceOverrides.single()
        assertFalse(effective.enabled)
        assertEquals(9, effective.preference.weight)
        assertEquals(1.0, effective.preference.targetValue)
        assertEquals(12.0, effective.preference.limitValue)
        assertEquals(false, override.enabled)
        assertEquals(9, override.weight)
        assertEquals(1.0, override.targetValue)
        assertEquals(12.0, override.limitValue)

        controller.resetPreference("forest.distance_km")
        advanceUntilIdle()

        val reset = controller.state.value.effectivePreferences.single()
        assertTrue(reset.enabled)
        assertEquals(5, reset.preference.weight)
        assertEquals(2.0, reset.preference.targetValue)
        assertEquals(10.0, reset.preference.limitValue)
        assertTrue(controller.state.value.preferenceOverrides.isEmpty())
        assertTrue(preferences.saved.last().preferenceOverrides.isEmpty())
    }

    @Test
    fun preferenceEditorRejectsInvalidThresholdOrdering() = runTest {
        val controller =
            AnalysisWorkspaceController(
                repository = FakeRepository(candidates = emptyList()),
                userPreferences = FakePreferencesRepository(null),
                scope = this,
                debounceMillis = 0,
            )
        controller.initialize()
        advanceUntilIdle()

        assertFailsWith<IllegalArgumentException> {
            controller.updatePreferenceThresholds(
                metricId = "forest.distance_km",
                targetValue = 12.0,
                limitValue = 2.0,
            )
        }
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
        runCurrent()
        controller.updateConditions(listOf(SearchCondition("hard", maxValue = 2.0)))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(listOf("new"), controller.state.value.rankedResults.map { it.settlement.id })

        advanceTimeBy(750)
        advanceUntilIdle()

        assertEquals(listOf("new"), controller.state.value.rankedResults.map { it.settlement.id })
    }

    @Test
    fun onlyCurrentAnalysisGenerationPublishesDiagnostics() = runTest {
        val repository = FakeRepository(candidates = listOf(candidate("initial", "Initial", 5.0)))
        val diagnostics = mutableListOf<SettlementAnalysisDiagnostics>()
        val controller =
            AnalysisWorkspaceController(
                repository = repository,
                userPreferences = FakePreferencesRepository(null),
                scope = this,
                debounceMillis = 250,
                onAnalysisDiagnostics = diagnostics::add,
            )

        controller.initialize()
        advanceUntilIdle()
        diagnostics.clear()
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
        advanceTimeBy(750)
        advanceUntilIdle()

        assertEquals(1, diagnostics.size)
        assertEquals(1, diagnostics.single().candidateCount)
        assertEquals(listOf("new"), controller.state.value.rankedResults.map { it.settlement.id })
    }

    @Test
    fun diagnosticsObserverFailureDoesNotChangeAnalysisResult() = runTest {
        val controller =
            AnalysisWorkspaceController(
                repository = FakeRepository(candidates = listOf(candidate("best", "Best", 1.0))),
                userPreferences = FakePreferencesRepository(null),
                scope = this,
                debounceMillis = 0,
                onAnalysisDiagnostics = { error("diagnostics sink failed") },
            )

        controller.initialize()
        advanceUntilIdle()

        assertEquals(listOf("best"), controller.state.value.rankedResults.map { it.settlement.id })
        assertNull(controller.state.value.failure)
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
    fun importedCandidateScopeRunsBoundedAnalysisEvenWithoutPreferencesOrHardFilters() = runTest {
        val repository =
            FakeRepository(
                candidates =
                    listOf(
                        candidate("a", "Alpha", forestDistance = 2.0),
                        candidate("b", "Beta", forestDistance = 3.0),
                    ),
                preferenceDefaults = emptyList(),
            )
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

        controller.updateCandidateScope(SettlementCandidateScope.Imported(listOf("b")))
        advanceUntilIdle()

        assertEquals(1, repository.analysisCalls)
        assertEquals(setOf("b"), repository.lastCandidateSettlementIds)
        assertEquals(listOf("b"), controller.state.value.rankedResults.map { it.settlement.id })
        assertEquals(SettlementCandidateScope.Imported(listOf("b")), controller.state.value.candidateScope)
    }


    @Test
    fun importedCandidateListCanBeDeactivatedReactivatedAndClearedWithoutLosingSourceText() = runTest {
        val repository =
            FakeRepository(
                candidates =
                    listOf(
                        candidate("a", "Alpha", forestDistance = 2.0),
                        candidate("b", "Beta", forestDistance = 3.0),
                    ),
            )
        val preferences = FakePreferencesRepository(null)
        val controller = AnalysisWorkspaceController(repository, preferences, this, debounceMillis = 0)

        controller.initialize()
        advanceUntilIdle()
        controller.applyImportedCandidates("Alpha\nBeta", listOf("a", "b"))
        advanceUntilIdle()

        assertEquals(SettlementCandidateScope.Imported(listOf("a", "b")), controller.state.value.candidateScope)
        assertEquals(ImportedCandidateList("Alpha\nBeta", listOf("a", "b")), controller.state.value.importedCandidateList)

        controller.deactivateImportedCandidates()
        advanceUntilIdle()
        assertEquals(SettlementCandidateScope.Dataset, controller.state.value.candidateScope)
        assertEquals(ImportedCandidateList("Alpha\nBeta", listOf("a", "b")), controller.state.value.importedCandidateList)
        assertEquals(null, repository.lastCandidateSettlementIds)

        controller.activateImportedCandidates()
        advanceUntilIdle()
        assertEquals(SettlementCandidateScope.Imported(listOf("a", "b")), controller.state.value.candidateScope)
        assertEquals(setOf("a", "b"), repository.lastCandidateSettlementIds)

        controller.clearImportedCandidates()
        advanceUntilIdle()
        assertEquals(SettlementCandidateScope.Dataset, controller.state.value.candidateScope)
        assertEquals(null, controller.state.value.importedCandidateList)
        assertEquals(null, preferences.saved.last().importedCandidateList)
    }

    @Test
    fun importedCandidateScopeRestoresByStableIdAndDropsOnlyStaleIds() = runTest {
        val alpha = settlement("a", "Alpha")
        val beta = settlement("b", "Beta")
        val repository =
            FakeRepository(
                candidates =
                    listOf(
                        candidate("a", "Alpha", forestDistance = 2.0),
                        candidate("b", "Beta", forestDistance = 3.0),
                    ),
                searchEntries =
                    listOf(
                        SettlementSearchEntry(alpha, emptyList()),
                        SettlementSearchEntry(beta, emptyList()),
                    ),
            )
        val preferences =
            FakePreferencesRepository(
                UserPreferences(
                    datasetId = "dataset",
                    candidateScope = SettlementCandidateScope.Imported(listOf("b", "gone", "a")),
                ),
            )
        val controller = AnalysisWorkspaceController(repository, preferences, this, debounceMillis = 0)

        controller.initialize()
        advanceUntilIdle()

        assertEquals(
            SettlementCandidateScope.Imported(listOf("b", "a")),
            controller.state.value.candidateScope,
        )
        assertEquals(setOf("a", "b"), repository.lastCandidateSettlementIds)
        assertEquals(
            SettlementCandidateScope.Imported(listOf("b", "a")),
            preferences.saved.last().candidateScope,
        )
    }

    @Test
    fun favoritesPersistWithoutRecalculationOrPreferenceWrites() = runTest {
        val repository = FakeRepository(candidates = listOf(candidate("best", "Best", 1.0)))
        val preferences = FakePreferencesRepository(null)
        val favorites = FakeFavoriteRepository()
        val controller =
            AnalysisWorkspaceController(
                repository = repository,
                userPreferences = preferences,
                scope = this,
                favoriteSettlements = favorites,
                debounceMillis = 0,
            )

        controller.initialize()
        advanceUntilIdle()
        val analysisCallsBefore = repository.analysisCalls
        val savesBefore = preferences.saved.size

        controller.addFavorite(controller.state.value.rankedResults.first { it.settlement.id == "best" })
        controller.addFavorite(
            ScoredSettlement(
                settlement = settlement("other", "Other"),
                score = SettlementScore(null, 0.0, 0, 0, emptyList()),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("other", "best"), controller.state.value.favorites.map { it.settlementId })
        assertEquals(analysisCallsBefore, repository.analysisCalls)
        assertEquals(savesBefore, preferences.saved.size)
        assertTrue(controller.state.value.favorites.all { it.analysisSnapshot != null })

        val originalSnapshot = controller.state.value.favorites.first { it.settlementId == "best" }.analysisSnapshot
        controller.updateFavoriteNote("best", "  promising location  ")
        advanceUntilIdle()
        assertEquals("promising location", controller.state.value.favorites.first { it.settlementId == "best" }.note)
        assertEquals(originalSnapshot, controller.state.value.favorites.first { it.settlementId == "best" }.analysisSnapshot)
        assertEquals(analysisCallsBefore, repository.analysisCalls)
        assertEquals(savesBefore, preferences.saved.size)

        controller.updateFavoriteSnapshot("best")
        advanceUntilIdle()
        assertEquals(analysisCallsBefore, repository.analysisCalls)
        assertEquals(savesBefore, preferences.saved.size)

        controller.removeFavorite("best")
        advanceUntilIdle()
        assertEquals(listOf("other"), controller.state.value.favorites.map { it.settlementId })

        controller.clearFavorites()
        advanceUntilIdle()
        assertTrue(controller.state.value.favorites.isEmpty())
        assertEquals(analysisCallsBefore, repository.analysisCalls)
        assertEquals(savesBefore, preferences.saved.size)
    }

    @Test
    fun favoriteSnapshotChangesOnlyAfterExplicitRefresh() = runTest {
        val repository = FakeRepository(candidates = listOf(candidate("best", "Best", 1.0)))
        val preferences = FakePreferencesRepository(null)
        val favorites = FakeFavoriteRepository()
        val controller = AnalysisWorkspaceController(repository, preferences, this, favorites, debounceMillis = 0)

        controller.initialize()
        advanceUntilIdle()
        controller.addFavorite(controller.state.value.rankedResults.single())
        advanceUntilIdle()
        val original = requireNotNull(controller.state.value.favorites.single().analysisSnapshot)

        controller.updatePreferenceWeight("forest.distance_km", 9)
        advanceUntilIdle()
        assertEquals(original, controller.state.value.favorites.single().analysisSnapshot)

        controller.updateFavoriteSnapshot("best")
        advanceUntilIdle()
        val refreshed = requireNotNull(controller.state.value.favorites.single().analysisSnapshot)
        assertEquals(9, refreshed.preferences.single().weight)
        assertTrue(refreshed.capturedAtEpochMs > original.capturedAtEpochMs)
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

    private class FakeFavoriteRepository : FavoriteSettlementRepository {
        private val entries = linkedMapOf<String, FavoriteSettlement>()
        private var clock = 0L

        override suspend fun list(datasetId: String): List<FavoriteSettlement> =
            entries.values.sortedWith(compareByDescending<FavoriteSettlement> { it.addedAtEpochMs }.thenBy { it.settlementId })

        override suspend fun add(
            datasetId: String,
            settlement: Settlement,
            analysisSnapshot: FavoriteAnalysisSnapshotDraft?,
        ): FavoriteSettlement {
            val existing = entries[settlement.id]
            if (existing != null) return existing
            clock += 1
            return FavoriteSettlement(
                datasetId,
                settlement.id,
                settlement.name,
                clock,
                analysisSnapshot = analysisSnapshot?.capturedAt(clock),
            ).also { entries[settlement.id] = it }
        }

        override suspend fun updateNote(datasetId: String, settlementId: String, note: String?): FavoriteSettlement {
            val current = requireNotNull(entries[settlementId])
            return current.copy(note = note?.trim()?.takeIf { it.isNotEmpty() }).also { entries[settlementId] = it }
        }

        override suspend fun updateSnapshot(
            datasetId: String,
            settlementId: String,
            analysisSnapshot: FavoriteAnalysisSnapshotDraft,
        ): FavoriteSettlement {
            val current = requireNotNull(entries[settlementId])
            clock += 1
            return current.copy(analysisSnapshot = analysisSnapshot.capturedAt(clock)).also { entries[settlementId] = it }
        }

        override suspend fun remove(datasetId: String, settlementId: String) {
            entries.remove(settlementId)
        }

        override suspend fun clear(datasetId: String) {
            entries.clear()
        }
    }

    private class FakeRepository(
        private val candidates: List<SettlementAnalysisCandidate>,
        private val detailsById: Map<String, SettlementDetails> = emptyMap(),
        private val searchEntries: List<SettlementSearchEntry> = emptyList(),
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
        var lastCandidateSettlementIds: Set<String>? = null
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

        override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> = searchEntries

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
            candidateSettlementIds: Set<String>?,
        ): List<SettlementAnalysisCandidate> {
            analysisCalls += 1
            lastConditions = conditions
            lastScoringMetricIds = scoringMetricIds
            lastCandidateSettlementIds = candidateSettlementIds
            val resolved = analysisHandler?.invoke(conditions) ?: candidates
            return if (candidateSettlementIds == null) {
                resolved
            } else {
                resolved.filter { it.settlement.id in candidateSettlementIds }
            }
        }

        override suspend fun details(settlementId: String): SettlementDetails =
            detailsById[settlementId] ?: error("Unknown settlement: $settlementId")
    }
}
