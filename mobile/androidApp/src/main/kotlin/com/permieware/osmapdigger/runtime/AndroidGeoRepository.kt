package com.permieware.osmapdigger.runtime

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.search.SettlementNameNormalizer

/** Android SQLite implementation over a generated read-only database. */
class AndroidGeoRepository(
    private val database: SQLiteDatabase,
    private val info: DatasetInfo,
) : GeoRepository {
    override suspend fun datasetInfo(): DatasetInfo = info

    override suspend fun metricDefinitions(): List<MetricDefinition> =
        database.rawQuery(
            """
            SELECT metric_id, category_id, group_id, title, description, unit,
                   measure_type, preferred_direction, default_enabled, sort_order
            FROM metric_definition
            ORDER BY sort_order
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(readMetricDefinition(cursor))
                }
            }
        }

    override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> =
        if (hasSettlementNameTable()) {
            readPersistedSettlementSearchEntries()
        } else {
            readLegacySettlementSearchEntries()
        }

    private fun hasSettlementNameTable(): Boolean =
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'settlement_name'",
            null,
        ).use { cursor -> cursor.moveToFirst() }

    private fun readPersistedSettlementSearchEntries(): List<SettlementSearchEntry> =
        database.rawQuery(
            """
            SELECT s.settlement_id, s.name, s.name_local, s.name_en, s.place_type,
                   s.population, s.latitude, s.longitude,
                   n.name, n.normalized_name, n.language, n.kind
            FROM settlement s
            LEFT JOIN settlement_name n ON n.settlement_id = s.settlement_id
            ORDER BY s.settlement_id,
                     CASE n.kind
                         WHEN 'primary' THEN 0
                         WHEN 'localized' THEN 1
                         WHEN 'official' THEN 2
                         ELSE 3
                     END,
                     n.name
            """.trimIndent(),
            null,
        ).use { cursor ->
            val entries = linkedMapOf<String, Pair<Settlement, MutableList<SettlementName>>>()
            while (cursor.moveToNext()) {
                val settlement = readSettlement(cursor)
                val pair = entries.getOrPut(settlement.id) { settlement to mutableListOf() }
                cursor.stringOrNull(8)?.let { name ->
                    pair.second +=
                        SettlementName(
                            value = name,
                            normalizedValue = cursor.getString(9),
                            language = cursor.stringOrNull(10),
                            kind = cursor.getString(11),
                        )
                }
            }
            entries.values.map { (settlement, names) ->
                SettlementSearchEntry(
                    settlement = settlement,
                    names = names.ifEmpty { legacyNames(settlement).toMutableList() },
                )
            }
        }

    private fun readLegacySettlementSearchEntries(): List<SettlementSearchEntry> =
        database.rawQuery(
            """
            SELECT settlement_id, name, name_local, name_en, place_type, population, latitude, longitude
            FROM settlement
            ORDER BY settlement_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val settlement = readSettlement(cursor)
                    add(SettlementSearchEntry(settlement, legacyNames(settlement)))
                }
            }
        }

    private fun legacyNames(settlement: Settlement): List<SettlementName> =
        listOfNotNull(
            settlement.name.takeIf { it.isNotBlank() }?.let {
                SettlementName(it, SettlementNameNormalizer.normalize(it), kind = "primary")
            },
            settlement.localName
                ?.takeIf { it.isNotBlank() && it != settlement.name }
                ?.let { SettlementName(it, SettlementNameNormalizer.normalize(it), kind = "localized") },
            settlement.englishName
                ?.takeIf { it.isNotBlank() && it != settlement.name && it != settlement.localName }
                ?.let {
                    SettlementName(
                        it,
                        SettlementNameNormalizer.normalize(it),
                        language = "en",
                        kind = "localized",
                    )
                },
        )

    /**
     * Apply the current hard-filter query through Android SQLite.
     *
     * This legacy path retains its name ordering and repository-side pre-limit for the
     * existing UI. Ranked analysis uses [analysisCandidates] instead.
     */
    override suspend fun searchCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        limit: Int,
    ): List<Settlement> {
        val sql =
            StringBuilder(
                """
                SELECT s.settlement_id, s.name, s.name_local, s.name_en, s.place_type,
                       s.population, s.latitude, s.longitude
                FROM settlement s
                WHERE 1 = 1
                """.trimIndent(),
            )
        val args = mutableListOf<String>()
        appendCandidatePredicates(sql, args, conditions, latitudeRange, longitudeRange)

        sql.append("\nORDER BY s.name LIMIT ?")
        args += limit.toString()
        return database.rawQuery(sql.toString(), args.toTypedArray()).use(::readSettlements)
    }

    /**
     * Return every hard-filter-eligible settlement with requested scoring metrics in one query.
     * Missing scoring rows remain absent from the candidate metric map rather than becoming zero.
     */
    override suspend fun analysisCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        scoringMetricIds: Set<String>,
    ): List<SettlementAnalysisCandidate> {
        require(scoringMetricIds.none { it.isBlank() }) { "Scoring metric IDs must not be blank" }
        val metricIds = scoringMetricIds.sorted()
        val args = mutableListOf<String>()
        val sql =
            if (metricIds.isEmpty()) {
                StringBuilder(
                    """
                    SELECT s.settlement_id, s.name, s.name_local, s.name_en, s.place_type,
                           s.population, s.latitude, s.longitude,
                           NULL AS scoring_metric_id, NULL AS scoring_metric_value
                    FROM settlement s
                    WHERE 1 = 1
                    """.trimIndent(),
                )
            } else {
                args.addAll(metricIds)
                StringBuilder(
                    """
                    SELECT s.settlement_id, s.name, s.name_local, s.name_en, s.place_type,
                           s.population, s.latitude, s.longitude,
                           sm_score.metric_id AS scoring_metric_id,
                           sm_score.value AS scoring_metric_value
                    FROM settlement s
                    LEFT JOIN settlement_metric sm_score
                      ON sm_score.settlement_id = s.settlement_id
                     AND sm_score.metric_id IN (${metricIds.joinToString(",") { "?" }})
                    WHERE 1 = 1
                    """.trimIndent(),
                )
            }

        appendCandidatePredicates(sql, args, conditions, latitudeRange, longitudeRange)
        sql.append("\nORDER BY s.settlement_id, scoring_metric_id")
        return database.rawQuery(sql.toString(), args.toTypedArray()).use(::readAnalysisCandidates)
    }

    private fun appendCandidatePredicates(
        sql: StringBuilder,
        args: MutableList<String>,
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
    ) {
        latitudeRange?.let {
            sql.append("\nAND s.latitude BETWEEN ? AND ?")
            args += it.start.toString()
            args += it.endInclusive.toString()
        }
        longitudeRange?.let {
            sql.append("\nAND s.longitude BETWEEN ? AND ?")
            args += it.start.toString()
            args += it.endInclusive.toString()
        }

        conditions.forEachIndexed { index, condition ->
            sql.append(
                """

                AND EXISTS (
                    SELECT 1 FROM settlement_metric sm$index
                    WHERE sm$index.settlement_id = s.settlement_id
                      AND sm$index.metric_id = ?
                """.trimIndent(),
            )
            args += condition.metricId
            condition.minValue?.let {
                sql.append("\n  AND sm$index.value >= ?")
                args += it.toString()
            }
            condition.maxValue?.let {
                sql.append("\n  AND sm$index.value <= ?")
                args += it.toString()
            }
            sql.append("\n)")
        }
    }

    /** Hydrate the selected settlement and its complete persisted metric set. */
    override suspend fun details(settlementId: String): SettlementDetails {
        val settlement =
            database.rawQuery(
                """
                SELECT settlement_id, name, name_local, name_en, place_type, population, latitude, longitude
                FROM settlement WHERE settlement_id = ?
                """.trimIndent(),
                arrayOf(settlementId),
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Settlement not found: $settlementId" }
                readSettlement(cursor)
            }

        val metrics =
            database.rawQuery(
                """
                SELECT d.metric_id, d.category_id, d.group_id, d.title, d.description,
                       d.unit, d.measure_type, d.preferred_direction, d.default_enabled,
                       d.sort_order, m.value
                FROM settlement_metric m
                JOIN metric_definition d ON d.metric_id = m.metric_id
                WHERE m.settlement_id = ?
                ORDER BY d.sort_order
                """.trimIndent(),
                arrayOf(settlementId),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(MetricValue(readMetricDefinition(cursor), cursor.getDouble(10)))
                    }
                }
            }

        return SettlementDetails(settlement, metrics)
    }

    private fun readAnalysisCandidates(cursor: Cursor): List<SettlementAnalysisCandidate> {
        val candidates =
            linkedMapOf<String, Pair<Settlement, MutableMap<String, Double>>>()
        while (cursor.moveToNext()) {
            val settlement = readSettlement(cursor)
            val candidate =
                candidates.getOrPut(settlement.id) {
                    settlement to linkedMapOf()
                }
            cursor.stringOrNull(8)?.let { metricId ->
                candidate.second[metricId] = cursor.getDouble(9)
            }
        }
        return candidates.values.map { (settlement, metricValues) ->
            SettlementAnalysisCandidate(settlement, metricValues.toMap())
        }
    }

    private fun readMetricDefinition(cursor: Cursor): MetricDefinition =
        MetricDefinition(
            id = cursor.getString(0),
            categoryId = cursor.getString(1),
            group = cursor.getString(2),
            title = cursor.getString(3),
            description = cursor.getString(4),
            unit = cursor.getString(5),
            measureType = cursor.getString(6),
            preferredDirection =
                when (cursor.getString(7)) {
                    "higher" -> PreferredDirection.HIGHER
                    "neutral" -> PreferredDirection.NEUTRAL
                    else -> PreferredDirection.LOWER
                },
            defaultEnabled = cursor.getInt(8) != 0,
            sortOrder = cursor.getInt(9),
        )

    private fun readSettlements(cursor: Cursor): List<Settlement> =
        buildList {
            while (cursor.moveToNext()) {
                add(readSettlement(cursor))
            }
        }

    private fun readSettlement(cursor: Cursor): Settlement =
        Settlement(
            id = cursor.getString(0),
            name = cursor.getString(1),
            localName = cursor.stringOrNull(2),
            englishName = cursor.stringOrNull(3),
            placeType = cursor.stringOrNull(4),
            population = cursor.longOrNull(5),
            location = GeoPoint(cursor.getDouble(6), cursor.getDouble(7)),
        )

    private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
    private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
}
