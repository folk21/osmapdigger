package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.dataset.GeoRepository
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.SettlementSearchEntry
import com.permieware.osmapdigger.domain.SettlementSearchMatch
import com.permieware.osmapdigger.geo.GeoMath

/** One normalized, de-duplicated line from a UTF-8 settlement-name import. */
data class SettlementImportLine(
    val sourceText: String,
    val normalizedText: String,
) {
    init {
        require(sourceText.isNotBlank()) { "Imported settlement text must not be blank" }
        require(normalizedText.isNotBlank()) { "Imported settlement normalized text must not be blank" }
    }
}

/** Conservative resolution result for one imported settlement name. */
sealed interface SettlementImportResolution {
    val line: SettlementImportLine

    data class Resolved(
        override val line: SettlementImportLine,
        val match: SettlementSearchMatch,
    ) : SettlementImportResolution

    data class Ambiguous(
        override val line: SettlementImportLine,
        val matches: List<SettlementSearchMatch>,
    ) : SettlementImportResolution {
        init {
            require(matches.size >= 2) { "Ambiguous import resolution requires at least two matches" }
        }
    }

    data class Unresolved(
        override val line: SettlementImportLine,
        val suggestions: List<SettlementSearchMatch>,
    ) : SettlementImportResolution
}

/** Ordered review result for one imported settlement-name list. */
data class SettlementImportReview(
    val resolutions: List<SettlementImportResolution>,
) {
    /** Stable IDs that were uniquely resolved, preserving reviewed import order. */
    val resolvedSettlementIds: List<String>
        get() {
            val seen = hashSetOf<String>()
            return resolutions
                .mapNotNull { (it as? SettlementImportResolution.Resolved)?.match?.settlement?.id }
                .filter { seen.add(it) }
        }
}

/** Apply explicit user choices to ambiguous/unresolved import rows without auto-accepting suggestions. */
object SettlementImportReviewer {
    fun reviewedSettlementIds(
        review: SettlementImportReview,
        selectedSettlementIdsByNormalizedLine: Map<String, Set<String>>,
    ): List<String> {
        val seen = hashSetOf<String>()
        val result = mutableListOf<String>()
        review.resolutions.forEach { resolution ->
            val settlementIds =
                when (resolution) {
                    is SettlementImportResolution.Resolved -> listOf(resolution.match.settlement.id)
                    is SettlementImportResolution.Ambiguous ->
                        selectedChoices(
                            resolution.line,
                            resolution.matches,
                            selectedSettlementIdsByNormalizedLine,
                        )
                    is SettlementImportResolution.Unresolved ->
                        selectedChoices(
                            resolution.line,
                            resolution.suggestions,
                            selectedSettlementIdsByNormalizedLine,
                        )
                }
            settlementIds.forEach { settlementId ->
                if (seen.add(settlementId)) {
                    result += settlementId
                }
            }
        }
        return result
    }

    private fun selectedChoices(
        line: SettlementImportLine,
        allowedMatches: List<SettlementSearchMatch>,
        selections: Map<String, Set<String>>,
    ): List<String> {
        val selectedIds = selections[line.normalizedText].orEmpty()
        if (selectedIds.isEmpty()) return emptyList()

        val allowedIds = allowedMatches.mapTo(hashSetOf()) { it.settlement.id }
        require(selectedIds.all { it in allowedIds }) {
            "Imported settlement choices must reference reviewed matches"
        }
        // Preserve the deterministic match order rather than Set iteration order.
        return allowedMatches.map { it.settlement.id }.filter { it in selectedIds }
    }
}

/** Parse the intentionally simple one-settlement-name-per-line import format. */
object SettlementListImportParser {
    fun parse(text: String): List<SettlementImportLine> {
        val seen = hashSetOf<String>()
        return text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { sourceText ->
                val normalized = SettlementNameNormalizer.normalize(sourceText)
                if (normalized.isBlank() || !seen.add(normalized)) {
                    null
                } else {
                    SettlementImportLine(sourceText = sourceText, normalizedText = normalized)
                }
            }
            .toList()
    }
}

/**
 * Resolve imported names against the complete dataset alias index without silently fuzzy-matching.
 *
 * When [center] and [radiusKm] are both provided, the existing shared Haversine distance algorithm
 * limits the resolution index before exact/partial/fuzzy matching. This keeps duplicate-name review
 * consistent with the active search area while retaining deterministic matching semantics.
 *
 * Only one exact normalized alias match is accepted automatically. Multiple exact matches remain
 * ambiguous, while prefix/substring/bounded-Levenshtein matches are suggestions for explicit review.
 */
class SettlementListImportResolver(
    private val repository: GeoRepository,
    private val suggestionLimit: Int = DEFAULT_SUGGESTION_LIMIT,
) {
    init {
        require(suggestionLimit >= 0) { "Suggestion limit must not be negative" }
    }

    suspend fun resolve(
        lines: List<SettlementImportLine>,
        center: GeoPoint? = null,
        radiusKm: Double? = null,
    ): SettlementImportReview {
        require(radiusKm == null || radiusKm.isFinite() && radiusKm > 0.0) {
            "Import review radius must be null or a positive finite value"
        }
        require(radiusKm == null || center != null) {
            "Import review radius requires a center"
        }

        val entries = repository.settlementSearchEntries().withinRadius(center, radiusKm)
        val exactAliasIndex = SettlementSearchMatcher.exactAliasIndex(entries)
        val resolutions =
            lines.map { line ->
                val exactMatches = exactAliasIndex[line.normalizedText].orEmpty()
                when (exactMatches.size) {
                    1 -> SettlementImportResolution.Resolved(line, exactMatches.single())
                    0 ->
                        SettlementImportResolution.Unresolved(
                            line = line,
                            suggestions =
                                if (suggestionLimit == 0) {
                                    emptyList()
                                } else {
                                    SettlementSearchMatcher.find(entries, line.normalizedText, suggestionLimit)
                                },
                        )
                    else -> SettlementImportResolution.Ambiguous(line, exactMatches)
                }
            }
        return SettlementImportReview(resolutions)
    }

    private fun List<SettlementSearchEntry>.withinRadius(
        center: GeoPoint?,
        radiusKm: Double?,
    ): List<SettlementSearchEntry> {
        if (center == null || radiusKm == null) return this
        return filter { entry ->
            GeoMath.distanceKm(center, entry.settlement.location) <= radiusKm
        }
    }

    companion object {
        const val DEFAULT_SUGGESTION_LIMIT: Int = 5
    }
}
