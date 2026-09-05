package com.permieware.osmapdigger.notebook

import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.error.OperationalFailure
import com.permieware.osmapdigger.error.OperationalFailureException
import com.permieware.osmapdigger.error.OperationalFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One deterministic file inside the portable Favorites export archive. */
data class FavoriteNotebookExportFile(
    val name: String,
    val content: ByteArray,
) {
    init {
        require(name.isNotBlank())
        require(!name.startsWith('/'))
        require(".." !in name.split('/'))
    }

    override fun equals(other: Any?): Boolean =
        other is FavoriteNotebookExportFile && name == other.name && content.contentEquals(other.content)

    override fun hashCode(): Int = 31 * name.hashCode() + content.contentHashCode()
}

/** Platform-neutral deterministic archive input prepared from one dataset-scoped notebook. */
data class FavoriteNotebookExportBundle(
    val fileName: String,
    val files: List<FavoriteNotebookExportFile>,
) {
    init {
        require(fileName.isNotBlank())
        require(files.isNotEmpty())
        require(files.map { it.name }.distinct().size == files.size)
    }
}

enum class FavoriteNotebookExportOutcome {
    COMPLETED,
    CANCELLED,
}

/** Platform adapter for saving or sharing a prepared notebook archive. */
interface FavoriteNotebookExporter {
    suspend fun export(bundle: FavoriteNotebookExportBundle): FavoriteNotebookExportOutcome
}

/** Classify platform file/share failures while preserving coroutine cancellation. */
class OperationalFavoriteNotebookExporter(
    private val delegate: FavoriteNotebookExporter,
) : FavoriteNotebookExporter {
    override suspend fun export(bundle: FavoriteNotebookExportBundle): FavoriteNotebookExportOutcome =
        try {
            delegate.export(bundle)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: OperationalFailureException) {
            throw failure
        } catch (failure: Throwable) {
            throw OperationalFailureException(
                OperationalFailure(
                    kind = OperationalFailureKind.FILE_ACCESS,
                    technicalMessage = "Could not export favorite notebook",
                    cause = failure,
                ),
            )
        }
}

/**
 * Build the versioned machine-readable manifest and human-readable Markdown from the same frozen
 * notebook model. Current localized settlement names are presentation-only and never replace saved identity.
 */
object FavoriteNotebookExportBuilder {
    const val ARCHIVE_FILE_NAME = "osmapdigger-favorites.zip"
    const val MANIFEST_FILE_NAME = "favorites.json"
    const val SUMMARY_FILE_NAME = "favorites.md"
    private const val CURRENT_VERSION = 1
    private const val SNAPSHOT_VERSION = 1
    private const val FORMAT_ID = "osmapdigger-favorites"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun build(
        datasetId: String,
        favorites: List<FavoriteSettlement>,
        currentDisplayNames: Map<String, String>,
    ): FavoriteNotebookExportBundle {
        require(datasetId.isNotBlank())
        require(favorites.isNotEmpty())
        require(favorites.all { it.datasetId == datasetId }) {
            "Favorite export may contain entries from only one dataset"
        }

        val entries =
            favorites
                .sortedWith(compareByDescending<FavoriteSettlement> { it.addedAtEpochMs }.thenBy { it.settlementId })
                .map { favorite ->
                    ExportFavorite(
                        settlementId = favorite.settlementId,
                        currentDisplayName = currentDisplayNames[favorite.settlementId]?.takeIf { it.isNotBlank() },
                        savedDisplayName = favorite.settlementName,
                        addedAtEpochMs = favorite.addedAtEpochMs,
                        note = favorite.note,
                        savedScoreValue = favorite.analysisSnapshot?.scoreValue,
                        savedCoverage = favorite.analysisSnapshot?.coverage,
                        analysisSnapshot = favorite.analysisSnapshot?.toPayload(),
                    )
                }
        val manifest = ExportManifest(
            format = FORMAT_ID,
            version = CURRENT_VERSION,
            datasetId = datasetId,
            favorites = entries,
        )
        val manifestJson = json.encodeToString(manifest)
        val markdown = renderMarkdown(manifest)

        return FavoriteNotebookExportBundle(
            fileName = ARCHIVE_FILE_NAME,
            files = listOf(
                FavoriteNotebookExportFile(MANIFEST_FILE_NAME, manifestJson.encodeToByteArray()),
                FavoriteNotebookExportFile(SUMMARY_FILE_NAME, markdown.encodeToByteArray()),
            ),
        )
    }

    @Serializable
    private data class ExportManifest(
        val format: String,
        val version: Int,
        val datasetId: String,
        val favorites: List<ExportFavorite>,
    )

    @Serializable
    private data class ExportFavorite(
        val settlementId: String,
        val currentDisplayName: String? = null,
        val savedDisplayName: String,
        val addedAtEpochMs: Long,
        val note: String? = null,
        val savedScoreValue: Double? = null,
        val savedCoverage: Double? = null,
        val analysisSnapshot: ExportSnapshot? = null,
    )

    @Serializable
    private data class ExportSnapshot(
        val version: Int,
        val datasetId: String,
        val settlementId: String,
        val settlementName: String,
        val capturedAtEpochMs: Long,
        val scoreValue: Double? = null,
        val coverage: Double,
        val knownWeight: Int,
        val totalWeight: Int,
        val centerSettlementId: String? = null,
        val centerSettlementName: String? = null,
        val radiusKm: Double? = null,
        val requiredCriteria: List<ExportRequiredCriterion>,
        val preferences: List<ExportPreference>,
    )

    @Serializable
    private data class ExportRequiredCriterion(
        val metricId: String,
        val title: String,
        val unit: String,
        val minValue: Double? = null,
        val maxValue: Double? = null,
    )

    @Serializable
    private data class ExportPreference(
        val metricId: String,
        val title: String,
        val unit: String,
        val direction: String,
        val targetValue: Double,
        val limitValue: Double,
        val weight: Int,
        val rawValue: Double? = null,
        val quality: Double? = null,
        val weightedContribution: Double? = null,
        val scoreContribution: Double? = null,
    )

    private fun FavoriteAnalysisSnapshot.toPayload() = ExportSnapshot(
        version = SNAPSHOT_VERSION,
        datasetId = datasetId,
        settlementId = settlementId,
        settlementName = settlementName,
        capturedAtEpochMs = capturedAtEpochMs,
        scoreValue = scoreValue,
        coverage = coverage,
        knownWeight = knownWeight,
        totalWeight = totalWeight,
        centerSettlementId = centerSettlementId,
        centerSettlementName = centerSettlementName,
        radiusKm = radiusKm,
        requiredCriteria = requiredCriteria.map {
            ExportRequiredCriterion(
                metricId = it.metricId,
                title = it.title,
                unit = it.unit,
                minValue = it.minValue,
                maxValue = it.maxValue,
            )
        },
        preferences = preferences.map {
            ExportPreference(
                metricId = it.metricId,
                title = it.title,
                unit = it.unit,
                direction = it.direction.name,
                targetValue = it.targetValue,
                limitValue = it.limitValue,
                weight = it.weight,
                rawValue = it.rawValue,
                quality = it.quality,
                weightedContribution = it.weightedContribution,
                scoreContribution = it.scoreContribution,
            )
        },
    )

    private fun renderMarkdown(manifest: ExportManifest): String = buildString {
        appendLine("# OsmapDigger Favorites")
        appendLine()
        appendLine("- Dataset ID: `${escapeCode(manifest.datasetId)}`")
        appendLine("- Favorites: ${manifest.favorites.size}")

        manifest.favorites.forEachIndexed { index, favorite ->
            val displayName = favorite.currentDisplayName ?: favorite.savedDisplayName
            appendLine()
            appendLine("## ${index + 1}. ${escapeMarkdown(displayName)}")
            appendLine()
            appendLine("- Settlement ID: `${escapeCode(favorite.settlementId)}`")
            appendLine("- Saved name: ${escapeMarkdown(favorite.savedDisplayName)}")
            favorite.currentDisplayName?.let {
                appendLine("- Current display name: ${escapeMarkdown(it)}")
            }
            favorite.note?.let { appendLine("- Note: ${escapeMarkdown(it)}") }

            favorite.analysisSnapshot?.let { snapshot ->
                appendLine()
                appendLine("### Saved analysis")
                appendLine()
                appendLine("- Snapshot version: ${snapshot.version}")
                snapshot.scoreValue?.let { appendLine("- Score: $it") }
                if (snapshot.totalWeight > 0) {
                    appendLine("- Data coverage: ${snapshot.coverage}% (${snapshot.knownWeight}/${snapshot.totalWeight} weight)")
                }
                if (snapshot.centerSettlementId != null && snapshot.centerSettlementName != null) {
                    appendLine("- Center: ${escapeMarkdown(snapshot.centerSettlementName)} (`${escapeCode(snapshot.centerSettlementId)}`)")
                }
                snapshot.radiusKm?.let { appendLine("- Radius: $it km") }

                if (snapshot.requiredCriteria.isNotEmpty()) {
                    appendLine()
                    appendLine("#### Required criteria")
                    snapshot.requiredCriteria.forEach { criterion ->
                        val bounds = buildList {
                            criterion.minValue?.let { add("min $it") }
                            criterion.maxValue?.let { add("max $it") }
                        }.joinToString(", ")
                        val unit = criterion.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                        appendLine("- ${escapeMarkdown(criterion.title)} (`${escapeCode(criterion.metricId)}`): ${escapeMarkdown(bounds)}$unit")
                    }
                }

                if (snapshot.preferences.isNotEmpty()) {
                    appendLine()
                    appendLine("#### Preferences")
                    snapshot.preferences.forEach { preference ->
                        val raw = preference.rawValue?.let { ", value $it" }.orEmpty()
                        appendLine(
                            "- ${escapeMarkdown(preference.title)} (`${escapeCode(preference.metricId)}`): " +
                                "${directionLabel(preference.direction)}, target ${preference.targetValue}, " +
                                "limit ${preference.limitValue}, weight ${preference.weight}$raw",
                        )
                    }
                }
            }
        }
    }.trimEnd() + "\n"

    private fun directionLabel(direction: String): String = when (direction) {
        PreferredDirection.LOWER.name -> "lower is better"
        PreferredDirection.HIGHER.name -> "higher is better"
        else -> direction.lowercase()
    }

    private fun escapeCode(value: String): String = value.replace("`", "\\`")

    private fun escapeMarkdown(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (character in MARKDOWN_SPECIAL_CHARACTERS) append('\\')
            append(character)
        }
    }

    private val MARKDOWN_SPECIAL_CHARACTERS = setOf('\\', '`', '*', '_', '[', ']', '<', '>', '#')
}
