package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.dataset.GeoRepository
import com.permieware.osmapdigger.domain.SettlementSearchMatch

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
        selectedSettlementIdByNormalizedLine: Map<String, String>,
    ): List<String> {
        val seen = hashSetOf<String>()
        val result = mutableListOf<String>()
        review.resolutions.forEach { resolution ->
            val settlementId =
                when (resolution) {
                    is SettlementImportResolution.Resolved -> resolution.match.settlement.id
                    is SettlementImportResolution.Ambiguous ->
                        selectedChoice(
                            resolution.line,
                            resolution.matches,
                            selectedSettlementIdByNormalizedLine,
                        )
                    is SettlementImportResolution.Unresolved ->
                        selectedChoice(
                            resolution.line,
                            resolution.suggestions,
                            selectedSettlementIdByNormalizedLine,
                        )
                }
            if (settlementId != null && seen.add(settlementId)) {
                result += settlementId
            }
        }
        return result
    }

    private fun selectedChoice(
        line: SettlementImportLine,
        allowedMatches: List<SettlementSearchMatch>,
        selections: Map<String, String>,
    ): String? {
        val selectedId = selections[line.normalizedText] ?: return null
        require(allowedMatches.any { it.settlement.id == selectedId }) {
            "Imported settlement choice must reference one of the reviewed matches"
        }
        return selectedId
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

    suspend fun resolve(lines: List<SettlementImportLine>): SettlementImportReview {
        val entries = repository.settlementSearchEntries()
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

    companion object {
        const val DEFAULT_SUGGESTION_LIMIT: Int = 5
    }
}
