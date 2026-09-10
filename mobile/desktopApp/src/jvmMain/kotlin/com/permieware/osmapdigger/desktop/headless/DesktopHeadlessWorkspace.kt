package com.permieware.osmapdigger.desktop.headless

import com.permieware.osmapdigger.analysis.ImportedCandidateList
import com.permieware.osmapdigger.analysis.ScoredSettlement
import com.permieware.osmapdigger.dataset.GeoRepository
import com.permieware.osmapdigger.desktop.notebook.FavoriteNotebookZipWriter
import com.permieware.osmapdigger.desktop.notebook.SqliteFavoriteSettlementRepository
import com.permieware.osmapdigger.desktop.preferences.SqliteUserPreferencesRepository
import com.permieware.osmapdigger.desktop.runtime.DesktopDataset
import com.permieware.osmapdigger.desktop.settings.SqliteExternalSearchProviderRepository
import com.permieware.osmapdigger.domain.SearchCondition
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.external.ExternalSearchBatchAction
import com.permieware.osmapdigger.external.ExternalSearchBatchBuilder
import com.permieware.osmapdigger.external.ExternalSearchProviderRepository
import com.permieware.osmapdigger.notebook.FavoriteNotebookExportBuilder
import com.permieware.osmapdigger.notebook.FavoriteSettlement
import com.permieware.osmapdigger.preferences.UserPreferences
import com.permieware.osmapdigger.preferences.UserPreferencesRepository
import com.permieware.osmapdigger.search.SettlementImportResolution
import com.permieware.osmapdigger.search.SettlementListImportParser
import com.permieware.osmapdigger.search.SettlementListImportResolver
import com.permieware.osmapdigger.workspace.AnalysisWorkspaceController
import com.permieware.osmapdigger.workspace.AnalysisWorkspaceState
import com.permieware.osmapdigger.workspace.FavoriteCandidateTransfer
import java.io.Closeable
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Desktop headless adapter over the same shared application controller used by Compose UI.
 *
 * Business rules remain in shared application/domain services. This adapter only composes real
 * Desktop persistence and provides deterministic helpers for developer automation and acceptance tests.
 */
class DesktopHeadlessWorkspace internal constructor(
    val repository: GeoRepository,
    settingsDatabase: Path,
    private val ownedResource: Closeable? = null,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val preferences: UserPreferencesRepository = SqliteUserPreferencesRepository(settingsDatabase)
    private val favorites = SqliteFavoriteSettlementRepository(settingsDatabase)
    private val providers: ExternalSearchProviderRepository = SqliteExternalSearchProviderRepository.create(settingsDatabase)

    val controller = AnalysisWorkspaceController(
        repository = repository,
        userPreferences = preferences,
        scope = scope,
        favoriteSettlements = favorites,
        debounceMillis = 0,
    )

    suspend fun initialize(): AnalysisWorkspaceState {
        controller.initialize()
        return awaitSettled()
    }

    suspend fun state(): AnalysisWorkspaceState = awaitSettled()

    /** Import only uniquely resolved exact settlement names; ambiguous/unresolved rows remain unapplied. */
    suspend fun importCandidates(sourceText: String): HeadlessImportResult {
        val lines = SettlementListImportParser.parse(sourceText)
        val review = SettlementListImportResolver(repository).resolve(lines)
        val unresolved = review.resolutions.filterNot { it is SettlementImportResolution.Resolved }
        if (unresolved.isNotEmpty()) {
            return HeadlessImportResult(review.resolvedSettlementIds, unresolved)
        }
        controller.applyImportedCandidates(sourceText, review.resolvedSettlementIds)
        awaitSettled()
        return HeadlessImportResult(review.resolvedSettlementIds, emptyList())
    }

    suspend fun setRequiredCondition(condition: SearchCondition) {
        val current = controller.state.value.conditions.filterNot { it.metricId == condition.metricId }
        controller.updateConditions(current + condition)
        awaitSettled()
    }

    suspend fun removeRequiredCondition(metricId: String) {
        controller.updateConditions(controller.state.value.conditions.filterNot { it.metricId == metricId })
        awaitSettled()
    }

    suspend fun deactivateImportedCandidates() {
        controller.deactivateImportedCandidates()
        awaitSettled()
    }

    suspend fun activateImportedCandidates() {
        controller.activateImportedCandidates()
        awaitSettled()
    }

    suspend fun clearImportedCandidates() {
        controller.clearImportedCandidates()
        awaitSettled()
    }

    suspend fun setCenter(settlementId: String?) {
        controller.updateCenter(settlementId?.let { resolveSettlement(it) })
        awaitSettled()
    }

    suspend fun setRadiusKm(radiusKm: Double?) {
        controller.updateRadiusKm(radiusKm)
        awaitSettled()
    }

    suspend fun setPreferenceEnabled(metricId: String, enabled: Boolean) {
        controller.updatePreferenceEnabled(metricId, enabled)
        awaitSettled()
    }

    suspend fun setPreferenceWeight(metricId: String, weight: Int) {
        controller.updatePreferenceWeight(metricId, weight)
        awaitSettled()
    }

    suspend fun setPreferenceThresholds(metricId: String, targetValue: Double, limitValue: Double) {
        controller.updatePreferenceThresholds(metricId, targetValue, limitValue)
        awaitSettled()
    }

    suspend fun resetPreference(metricId: String) {
        controller.resetPreference(metricId)
        awaitSettled()
    }

    suspend fun addFavorite(settlementId: String) {
        controller.addFavorite(rankedResult(settlementId))
        awaitFavorite(settlementId) { true }
    }

    suspend fun updateFavoriteNote(settlementId: String, note: String?) {
        val normalized = note?.trim()?.takeIf { it.isNotEmpty() }
        controller.updateFavoriteNote(settlementId, note)
        awaitFavorite(settlementId) { it.note == normalized }
    }

    suspend fun removeFavorite(settlementId: String) {
        controller.removeFavorite(settlementId)
        withTimeout(timeoutMillis) {
            while (controller.state.value.favorites.any { it.settlementId == settlementId }) delay(POLL_MILLIS)
        }
    }

    suspend fun clearFavorites() {
        controller.clearFavorites()
        withTimeout(timeoutMillis) {
            while (controller.state.value.favorites.isNotEmpty()) delay(POLL_MILLIS)
        }
    }

    suspend fun updateFavoriteSnapshot(settlementId: String) {
        val before = controller.state.value.favorites.firstOrNull { it.settlementId == settlementId }?.analysisSnapshot
        controller.updateFavoriteSnapshot(settlementId)
        awaitFavorite(settlementId) { favorite ->
            val current = favorite.analysisSnapshot
            current != null && (before == null || current != before)
        }
    }

    suspend fun copyFavoritesToImported(selectedSettlementIds: Set<String>): ImportedCandidateList {
        val state = awaitSettled()
        val availableIds = repository.settlementSearchEntries().mapTo(hashSetOf()) { it.settlement.id }
        val imported = requireNotNull(
            FavoriteCandidateTransfer.build(state.favorites, selectedSettlementIds, availableIds),
        ) { "No selected available Favorites can be copied to imported candidates" }
        controller.applyImportedCandidates(imported.sourceText, imported.settlementIds)
        awaitSettled()
        return imported
    }

    suspend fun buildBatchSearch(
        providerId: String,
        selectedSettlementIds: Set<String>,
    ): List<ExternalSearchBatchAction> {
        val state = awaitSettled()
        val info = requireNotNull(state.datasetInfo)
        val selectedFavorites = state.favorites.filter { it.settlementId in selectedSettlementIds }
        require(selectedFavorites.isNotEmpty()) { "No selected Favorites are available for batch search" }
        val currentNames = repository.settlementSearchEntries().associate { it.settlement.id to it.settlement.name }
        val provider = providers.providersFor(info.countryCode).firstOrNull { it.id == providerId }
            ?: error("External-search provider '$providerId' is not available for ${info.countryCode}")
        return ExternalSearchBatchBuilder.build(
            providers = listOf(provider),
            settlementNames = selectedFavorites.map { currentNames[it.settlementId] ?: it.settlementName },
            terms = info.propertySearchTerms,
        )
    }

    suspend fun exportFavorites(destination: Path) {
        val state = awaitSettled()
        val info = requireNotNull(state.datasetInfo)
        require(state.favorites.isNotEmpty()) { "Favorites notebook is empty" }
        val currentNames = repository.settlementSearchEntries().associate { it.settlement.id to it.settlement.name }
        val bundle = FavoriteNotebookExportBuilder.build(info.id, state.favorites, currentNames)
        FavoriteNotebookZipWriter.write(bundle, destination.toFile())
    }

    suspend fun awaitSettled(): AnalysisWorkspaceState = withTimeout(timeoutMillis) {
        controller.awaitPendingWork()
        val state = controller.state.first { it.initialized && !it.analyzing }
        awaitPreferences(state)
        controller.state.value
    }

    private suspend fun awaitPreferences(expectedState: AnalysisWorkspaceState) {
        val expected = expectedState.toUserPreferences() ?: return
        while (preferences.load() != expected) {
            delay(POLL_MILLIS)
        }
    }

    private suspend fun awaitFavorite(
        settlementId: String,
        predicate: (FavoriteSettlement) -> Boolean,
    ) = withTimeout(timeoutMillis) {
        while (true) {
            val favorite = controller.state.value.favorites.firstOrNull { it.settlementId == settlementId }
            if (favorite != null && predicate(favorite)) return@withTimeout
            delay(POLL_MILLIS)
        }
    }

    private suspend fun resolveSettlement(settlementId: String): Settlement =
        repository.settlementSearchEntries().firstOrNull { it.settlement.id == settlementId }?.settlement
            ?: error("Settlement '$settlementId' is not available in the dataset")

    private fun rankedResult(settlementId: String): ScoredSettlement =
        controller.state.value.rankedCandidatesBySettlementId[settlementId]?.result
            ?: error("Settlement '$settlementId' is not eligible in the current analysis")

    private fun AnalysisWorkspaceState.toUserPreferences(): UserPreferences? {
        val info = datasetInfo ?: return null
        return UserPreferences(
            datasetId = info.id,
            centerSettlementId = center?.id,
            centerSettlementName = center?.name,
            radiusKm = radiusKm,
            conditions = conditions,
            preferenceOverrides = preferenceOverrides,
            candidateScope = candidateScope,
            importedCandidateList = importedCandidateList,
        )
    }

    override fun close() {
        scope.cancel()
        ownedResource?.close()
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 5_000L
        private const val POLL_MILLIS = 10L

        /** Open a real generated Desktop package while keeping mutable application state in [settingsDatabase]. */
        fun open(datasetDirectory: Path, settingsDatabase: Path): DesktopHeadlessWorkspace {
            val dataset = DesktopDataset.open(datasetDirectory)
            return DesktopHeadlessWorkspace(
                repository = dataset.runtime.repository,
                settingsDatabase = settingsDatabase,
                ownedResource = dataset,
            )
        }
    }
}

data class HeadlessImportResult(
    val resolvedSettlementIds: List<String>,
    val unresolved: List<SettlementImportResolution>,
) {
    val applied: Boolean get() = unresolved.isEmpty()
}
