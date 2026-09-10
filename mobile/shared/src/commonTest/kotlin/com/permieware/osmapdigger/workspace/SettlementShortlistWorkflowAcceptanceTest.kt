package com.permieware.osmapdigger.workspace

import com.permieware.osmapdigger.analysis.ImportedCandidateList
import com.permieware.osmapdigger.analysis.MetricPreference
import com.permieware.osmapdigger.analysis.SettlementAnalysisRequest
import com.permieware.osmapdigger.analysis.SettlementAnalysisService
import com.permieware.osmapdigger.analysis.SettlementCandidateScope
import com.permieware.osmapdigger.dataset.GeoRepository
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.MetricPreferenceDefault
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.SearchRequest
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementAnalysisCandidate
import com.permieware.osmapdigger.domain.SettlementDetails
import com.permieware.osmapdigger.domain.SettlementName
import com.permieware.osmapdigger.domain.SettlementSearchEntry
import com.permieware.osmapdigger.external.ExternalSearchBatchBuilder
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.notebook.FavoriteAnalysisSnapshotCodec
import com.permieware.osmapdigger.notebook.FavoriteNotebookExportBuilder
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference
import com.permieware.osmapdigger.preferences.SettlementCandidateScopePayloadCodec
import com.permieware.osmapdigger.presentation.PreferenceDataAvailabilityBuilder
import com.permieware.osmapdigger.presentation.ScoreExplanationBuilder
import com.permieware.osmapdigger.search.SettlementImportReviewer
import com.permieware.osmapdigger.search.SettlementListImportParser
import com.permieware.osmapdigger.search.SettlementListImportResolver
import com.permieware.osmapdigger.search.SettlementNameNormalizer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Cross-feature regression fixture for the shared settlement shortlist product contract. */
class SettlementShortlistWorkflowAcceptanceTest {
    @Test
    fun importedCandidatesFlowThroughRankingNotebookReanalysisBatchSearchAndExport() = runTest {
        val center = settlement("center", "Центр", 0.0, 0.0)
        val alpha = settlement("alpha", "Альфа", 0.010, 0.0)
        val beta = settlement("beta", "Бета", 0.020, 0.0)
        val gamma = settlement("gamma", "Гамма", 0.030, 0.0)
        val far = settlement("far", "Дальняя", 0.040, 0.040)
        val unimportedBest = settlement("best", "Идеальная", 0.005, 0.0)
        val records =
            listOf(
                record(alpha, forest = 0.5, industrial = 9.0, medical = 4.0, landfill = 10.0),
                record(beta, forest = 0.2, industrial = 10.0, medical = 1.0, landfill = 2.0),
                record(gamma, forest = 2.0, industrial = 5.0, medical = null, landfill = 8.0),
                record(far, forest = 0.1, industrial = 10.0, medical = 1.0, landfill = 9.0),
                record(unimportedBest, forest = 0.0, industrial = 12.0, medical = 0.5, landfill = 20.0),
            )
        val repository = FixtureRepository(records)

        val lines =
            SettlementListImportParser.parse(
                """
                Альфа
                Бета
                Гамма
                Дальняя
                Неизвестная
                альфа
                """.trimIndent(),
            )
        assertEquals(5, lines.size, "normalized duplicate import lines must collapse")

        val review = SettlementListImportResolver(repository).resolve(lines)
        val reviewedIds = SettlementImportReviewer.reviewedSettlementIds(review, emptyMap())
        assertEquals(listOf("alpha", "beta", "gamma", "far"), reviewedIds)

        val importedList =
            ImportedCandidateList(
                sourceText = lines.joinToString("\n") { it.sourceText },
                settlementIds = reviewedIds,
            )
        val persistedPayload =
            SettlementCandidateScopePayloadCodec.encode(
                scope = SettlementCandidateScope.Imported(reviewedIds),
                importedCandidateList = importedList,
            )
        val restoredSource = assertNotNull(SettlementCandidateScopePayloadCodec.decode(persistedPayload))
        assertEquals(reviewedIds, (restoredSource.candidateScope as SettlementCandidateScope.Imported).settlementIds)
        assertEquals(importedList, restoredSource.importedCandidateList)

        val preferences = preferences()
        val required = SearchCondition("landfill.distance_km", minValue = 5.0)
        val outcome =
            SettlementAnalysisService(repository).analyzeWithDiagnostics(
                SettlementAnalysisRequest(
                    search =
                        SearchRequest(
                            center = center,
                            radiusKm = 5.0,
                            conditions = listOf(required),
                            limit = 1,
                        ),
                    preferences = preferences,
                    candidateScope = restoredSource.candidateScope,
                ),
            )

        assertEquals(setOf("alpha", "beta", "gamma", "far"), repository.requestedCandidateIds)
        assertEquals(3, outcome.diagnostics.candidateCount, "Required filtering happens before shared radius scoring")
        assertEquals(2, outcome.diagnostics.exactEligibleCandidateCount, "far corner candidate must fail exact radius")
        assertEquals(listOf("alpha"), outcome.results.map { it.settlement.id })
        assertEquals(
            listOf(1 to "alpha", 2 to "gamma"),
            outcome.rankedCandidates.map { it.rank to it.result.settlement.id },
            "complete ranking must survive the visible top-N limit",
        )
        assertEquals(0, repository.detailsCalls, "ranked analysis must not hydrate per-candidate details")

        val alphaRanked = outcome.rankedCandidates.first().result
        val gammaRanked = outcome.rankedCandidates.last().result
        assertEquals(95.0, alphaRanked.score.value ?: error("alpha score missing"), 1e-9)
        assertEquals(65.625, gammaRanked.score.value ?: error("gamma score missing"), 1e-9)
        assertEquals(80.0, gammaRanked.score.coverage, 1e-9)

        val baseline = ScoreExplanationBuilder.baseline(outcome.rankedCandidates.map { it.result.score })
        val definitions = fixtureMetricDefinitions()
        val definitionsById = definitions.associateBy { it.id }
        val alphaExplanation = ScoreExplanationBuilder.build(alphaRanked.score, definitionsById, baseline)
        val gammaExplanation = ScoreExplanationBuilder.build(gammaRanked.score, definitionsById, baseline)
        assertEquals("industrial.distance_km", alphaExplanation.advantage?.contribution?.metricId)
        assertEquals("industrial.distance_km", gammaExplanation.compromise?.contribution?.metricId)
        assertEquals(
            listOf("medical.distance_km"),
            PreferenceDataAvailabilityBuilder.build(gammaRanked.score).missingMetricIds,
        )

        val effectivePreferences =
            preferences.map { preference ->
                EffectiveMetricPreference(
                    metricId = preference.metricId,
                    enabled = true,
                    preference = preference,
                )
            }
        val gammaSnapshot =
            FavoriteAnalysisSnapshotFactory.create(
                datasetId = DATASET_ID,
                result = gammaRanked,
                definitions = definitions,
                conditions = listOf(required),
                effectivePreferences = effectivePreferences,
                center = center,
                radiusKm = 5.0,
            ).capturedAt(200L)
        assertEquals(gammaSnapshot, FavoriteAnalysisSnapshotCodec.decode(FavoriteAnalysisSnapshotCodec.encode(gammaSnapshot)))
        assertEquals(1, gammaSnapshot.requiredCriteria.size)
        assertEquals(null, gammaSnapshot.preferences.single { it.metricId == "medical.distance_km" }.quality)

        val favorites =
            listOf(
                FavoriteSettlement(DATASET_ID, "alpha", "Альфа", 300L),
                FavoriteSettlement(
                    DATASET_ID,
                    "gamma",
                    "Гамма",
                    200L,
                    note = "Проверить участок",
                    analysisSnapshot = gammaSnapshot,
                ),
                FavoriteSettlement(DATASET_ID, "stale", "Старая деревня", 100L),
            )
        val transferred =
            assertNotNull(
                FavoriteCandidateTransfer.build(
                    favorites = favorites,
                    selectedSettlementIds = setOf("alpha", "gamma", "stale"),
                    availableSettlementIds = setOf("alpha", "gamma"),
                ),
            )
        assertEquals(listOf("alpha", "gamma"), transferred.settlementIds)
        assertEquals("Альфа\nГамма", transferred.sourceText)

        val batchActions =
            ExternalSearchBatchBuilder.build(
                providers =
                    listOf(
                        ExternalSearchProvider(
                            id = "search",
                            title = "Search",
                            countryCode = "BY",
                            urlTemplate = "https://example.test/search?q={query}",
                            priority = 1,
                            queryTermsOverride = "дом недвижимость",
                        ),
                    ),
                settlementNames = transferred.sourceText.lines(),
            )
        assertEquals(1, batchActions.size)
        assertEquals(listOf("Альфа", "Гамма"), batchActions.single().settlementNames)
        assertFalse(batchActions.single().url.any { it.code > 127 }, "batch URL must percent-encode non-ASCII text")
        assertTrue(batchActions.single().url.length <= ExternalSearchBatchBuilder.DEFAULT_MAX_URL_LENGTH)

        val export =
            FavoriteNotebookExportBuilder.build(
                datasetId = DATASET_ID,
                favorites = favorites,
                currentDisplayNames = mapOf("alpha" to "Альфа", "gamma" to "Гамма"),
            )
        val repeatedExport =
            FavoriteNotebookExportBuilder.build(
                datasetId = DATASET_ID,
                favorites = favorites.reversed(),
                currentDisplayNames = mapOf("alpha" to "Альфа", "gamma" to "Гамма"),
            )
        export.files.zip(repeatedExport.files).forEach { (first, second) ->
            assertEquals(first.name, second.name)
            assertContentEquals(first.content, second.content)
        }
        val manifest = export.files.first { it.name == FavoriteNotebookExportBuilder.MANIFEST_FILE_NAME }.content.decodeToString()
        assertTrue(manifest.contains("\"settlementId\": \"stale\""), "unavailable Favorites must remain exportable")
        assertTrue(manifest.contains("Проверить участок"))
        assertTrue(manifest.contains("\"coverage\": 80.0"))
        assertFalse(manifest.contains("/Users/"))
    }

    private fun preferences(): List<MetricPreference> =
        listOf(
            MetricPreference("forest.distance_km", PreferredDirection.LOWER, 1.0, 5.0, 5),
            MetricPreference("industrial.distance_km", PreferredDirection.HIGHER, 8.0, 2.0, 3),
            MetricPreference("medical.distance_km", PreferredDirection.LOWER, 2.0, 10.0, 2),
        )

    private fun fixtureMetricDefinitions(): List<MetricDefinition> =
        listOf(
            definition("forest.distance_km", "forest", "Forest distance", PreferredDirection.LOWER),
            definition("industrial.distance_km", "industrial", "Industrial distance", PreferredDirection.HIGHER),
            definition("medical.distance_km", "medical", "Medical distance", PreferredDirection.LOWER),
            definition("landfill.distance_km", "landfill", "Landfill distance", PreferredDirection.HIGHER),
        )

    private fun definition(
        id: String,
        categoryId: String,
        title: String,
        direction: PreferredDirection,
    ) = MetricDefinition(
        id = id,
        categoryId = categoryId,
        group = "Acceptance",
        title = title,
        description = title,
        unit = "km",
        measureType = "distance",
        preferredDirection = direction,
        defaultEnabled = false,
        sortOrder = 0,
    )

    private fun settlement(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double,
    ) = Settlement(
        id = id,
        name = name,
        localName = name,
        englishName = null,
        placeType = "village",
        population = null,
        location = GeoPoint(latitude, longitude),
    )

    private fun record(
        settlement: Settlement,
        forest: Double,
        industrial: Double,
        medical: Double?,
        landfill: Double,
    ): FixtureRecord {
        val metrics =
            buildMap {
                put("forest.distance_km", forest)
                put("industrial.distance_km", industrial)
                medical?.let { put("medical.distance_km", it) }
                put("landfill.distance_km", landfill)
            }
        return FixtureRecord(settlement, metrics)
    }

    private data class FixtureRecord(
        val settlement: Settlement,
        val metrics: Map<String, Double>,
    )

    private class FixtureRepository(
        private val records: List<FixtureRecord>,
    ) : GeoRepository {
        var requestedCandidateIds: Set<String>? = null
        var detailsCalls: Int = 0

        override suspend fun datasetInfo(): DatasetInfo =
            DatasetInfo(DATASET_ID, "Acceptance", "BY", GeoPoint(0.0, 0.0), 8.0, false, null, "property")

        override suspend fun metricDefinitions(): List<MetricDefinition> = emptyList()

        override suspend fun preferenceDefaults(): List<MetricPreferenceDefault> = emptyList()

        override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> =
            records.map { record ->
                val name = record.settlement.name
                SettlementSearchEntry(
                    settlement = record.settlement,
                    names =
                        listOf(
                            SettlementName(
                                value = name,
                                normalizedValue = SettlementNameNormalizer.normalize(name),
                                language = "ru",
                                kind = "canonical",
                            ),
                        ),
                )
            }

        override suspend fun searchCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            limit: Int,
        ): List<Settlement> = error("Legacy search path is not part of shortlist acceptance")

        override suspend fun analysisCandidates(
            conditions: List<SearchCondition>,
            latitudeRange: ClosedFloatingPointRange<Double>?,
            longitudeRange: ClosedFloatingPointRange<Double>?,
            scoringMetricIds: Set<String>,
            candidateSettlementIds: Set<String>?,
        ): List<SettlementAnalysisCandidate> {
            requestedCandidateIds = candidateSettlementIds
            return records
                .asSequence()
                .filter { candidateSettlementIds == null || it.settlement.id in candidateSettlementIds }
                .filter { latitudeRange == null || it.settlement.location.latitude in latitudeRange }
                .filter { longitudeRange == null || it.settlement.location.longitude in longitudeRange }
                .filter { record -> matchesRequired(record.metrics, conditions) }
                .map { record ->
                    SettlementAnalysisCandidate(
                        settlement = record.settlement,
                        metricValues = record.metrics.filterKeys { it in scoringMetricIds },
                    )
                }
                .toList()
        }

        override suspend fun details(settlementId: String): SettlementDetails {
            detailsCalls += 1
            error("Per-candidate details hydration must not be used by shortlist ranking")
        }

        private fun matchesRequired(
            metrics: Map<String, Double>,
            conditions: List<SearchCondition>,
        ): Boolean =
            conditions.all { condition ->
                val value = metrics[condition.metricId] ?: return@all false
                (condition.minValue == null || value >= condition.minValue) &&
                    (condition.maxValue == null || value <= condition.maxValue)
            }
    }

    private companion object {
        const val DATASET_ID = "acceptance-by"
    }
}
