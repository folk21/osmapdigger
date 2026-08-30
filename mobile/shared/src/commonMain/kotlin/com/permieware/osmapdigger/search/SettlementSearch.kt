package com.permieware.osmapdigger.search

import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.domain.SettlementMatchKind
import com.permieware.osmapdigger.domain.SettlementName
import com.permieware.osmapdigger.domain.SettlementSearchEntry
import com.permieware.osmapdigger.domain.SettlementSearchMatch
import com.permieware.osmapdigger.geo.GeoMath
import com.permieware.osmapdigger.dataset.GeoRepository

/** Shared normalization rules mirrored by the Geo Builder settlement-name writer. */
object SettlementNameNormalizer {
    fun normalize(value: String): String =
        buildString {
            var previousSpace = false
            value.trim().lowercase().replace('ё', 'е').forEach { char ->
                when {
                    char.isLetterOrDigit() -> {
                        append(char)
                        previousSpace = false
                    }
                    !previousSpace -> {
                        append(' ')
                        previousSpace = true
                    }
                }
            }
        }.trim()
}

/**
 * Deterministic multilingual settlement lookup shared by Android and Desktop.
 *
 * The complete alias index is loaded lazily once per opened repository. This keeps
 * fuzzy semantics platform-independent and avoids requiring SQLite ICU/FTS extensions.
 */
class SettlementSearchService(
    private val repository: GeoRepository,
) {
    private var cachedEntries: List<SettlementSearchEntry>? = null

    suspend fun find(query: String, limit: Int = 20): List<SettlementSearchMatch> {
        val normalizedQuery = SettlementNameNormalizer.normalize(query)
        if (normalizedQuery.isBlank() || limit <= 0) return emptyList()

        val entries = entries()
        return entries
            .mapNotNull { entry -> bestMatch(entry, normalizedQuery) }
            .sortedWith(matchComparator)
            .take(limit)
            .map { ranked ->
                SettlementSearchMatch(
                    settlement = ranked.entry.settlement,
                    matchedName = ranked.name.value,
                    aliases =
                        ranked.entry.names
                            .map { it.value }
                            .filterNot { it == ranked.entry.settlement.name }
                            .distinct(),
                    kind = ranked.kind,
                )
            }
    }


    /** Return the geographically nearest settlement from the complete dataset settlement index. */
    suspend fun nearestTo(location: GeoPoint): Settlement? =
        entries()
            .asSequence()
            .map { entry -> entry.settlement to GeoMath.distanceKm(location, entry.settlement.location) }
            .minWithOrNull(
                compareBy<Pair<Settlement, Double>>({ it.second }, { it.first.id }),
            )
            ?.first

    private suspend fun entries(): List<SettlementSearchEntry> =
        cachedEntries ?: repository.settlementSearchEntries().also { cachedEntries = it }

    private fun bestMatch(entry: SettlementSearchEntry, query: String): RankedMatch? =
        entry.names
            .asSequence()
            .mapNotNull { name -> rankName(entry, name, query) }
            .minWithOrNull(singleEntryComparator)

    private fun rankName(
        entry: SettlementSearchEntry,
        name: SettlementName,
        query: String,
    ): RankedMatch? {
        val candidate = name.normalizedValue.ifBlank { SettlementNameNormalizer.normalize(name.value) }
        if (candidate.isBlank()) return null

        return when {
            candidate == query -> RankedMatch(entry, name, SettlementMatchKind.EXACT, 0)
            candidate.startsWith(query) -> RankedMatch(entry, name, SettlementMatchKind.PREFIX, 0)
            candidate.contains(query) -> RankedMatch(entry, name, SettlementMatchKind.SUBSTRING, 0)
            else -> {
                val threshold = fuzzyThreshold(query.length)
                if (threshold == 0 || kotlin.math.abs(candidate.length - query.length) > threshold) return null
                val distance = levenshtein(query, candidate, threshold)
                if (distance <= threshold) {
                    RankedMatch(entry, name, SettlementMatchKind.FUZZY, distance)
                } else {
                    null
                }
            }
        }
    }

    private fun fuzzyThreshold(length: Int): Int =
        when {
            length < 4 -> 0
            length <= 5 -> 1
            length <= 9 -> 2
            else -> 3
        }

    /** Bounded Levenshtein distance; exits early once no row can satisfy maxDistance. */
    private fun levenshtein(left: String, right: String, maxDistance: Int): Int {
        if (left == right) return 0
        if (kotlin.math.abs(left.length - right.length) > maxDistance) return maxDistance + 1
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        left.forEachIndexed { leftIndex, leftChar ->
            current[0] = leftIndex + 1
            var rowMin = current[0]
            right.forEachIndexed { rightIndex, rightChar ->
                val substitution = previous[rightIndex] + if (leftChar == rightChar) 0 else 1
                current[rightIndex + 1] =
                    minOf(
                        previous[rightIndex + 1] + 1,
                        current[rightIndex] + 1,
                        substitution,
                    )
                rowMin = minOf(rowMin, current[rightIndex + 1])
            }
            if (rowMin > maxDistance) return maxDistance + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    private data class RankedMatch(
        val entry: SettlementSearchEntry,
        val name: SettlementName,
        val kind: SettlementMatchKind,
        val editDistance: Int,
    )

    private val singleEntryComparator =
        compareBy<RankedMatch>(
            { it.kind.ordinal },
            { it.editDistance },
            { it.name.normalizedValue.length },
            { it.name.value },
        )

    private val matchComparator =
        compareBy<RankedMatch>(
            { it.kind.ordinal },
            { it.editDistance },
            { -(it.entry.settlement.population ?: -1L) },
            { it.entry.settlement.name },
            { it.entry.settlement.id },
        )
}
