package com.permieware.osmapdigger.runtime

import com.permieware.osmapdigger.dataset.DatasetCandidateQueries
import com.permieware.osmapdigger.dataset.DatasetQueryArgument
import com.permieware.osmapdigger.dataset.DatasetQuerySpec
import com.permieware.osmapdigger.dataset.GeoRepository
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

    override suspend fun preferenceDefaults(): List<MetricPreferenceDefault> {
        if (!hasTable("metric_preference_default")) return emptyList()

        return database.rawQuery(
            """
            SELECT p.metric_id, p.direction, p.target_value, p.limit_value,
                   p.weight, p.default_enabled
            FROM metric_preference_default p
            JOIN metric_definition d ON d.metric_id = p.metric_id
            ORDER BY d.sort_order, p.metric_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MetricPreferenceDefault(
                            metricId = cursor.getString(0),
                            direction = readPreferenceDirection(cursor.getString(1)),
                            targetValue = cursor.getDouble(2),
                            limitValue = cursor.getDouble(3),
                            weight = cursor.getInt(4),
                            defaultEnabled = cursor.getInt(5) != 0,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun settlementSearchEntries(): List<SettlementSearchEntry> =
        if (hasSettlementNameTable()) {
            readPersistedSettlementSearchEntries()
        } else {
            readLegacySettlementSearchEntries()
        }

    private fun hasSettlementNameTable(): Boolean = hasTable("settlement_name")

    private fun hasTable(name: String): Boolean =
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(name),
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

    /** Execute the shared legacy hard-filter query through Android SQLite. */
    override suspend fun searchCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        limit: Int,
    ): List<Settlement> =
        executeSettlementQuery(
            DatasetCandidateQueries.searchCandidates(conditions, latitudeRange, longitudeRange, limit),
        )

    /**
     * Execute shared ranked-analysis query specifications through Android SQLite.
     *
     * SQL semantics and deterministic candidate batching are owned by [DatasetCandidateQueries];
     * Android remains responsible only for selection-argument conversion, cursor execution, and mapping.
     */
    override suspend fun analysisCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        scoringMetricIds: Set<String>,
        candidateSettlementIds: Set<String>?,
    ): List<SettlementAnalysisCandidate> =
        DatasetCandidateQueries
            .analysisCandidates(
                conditions,
                latitudeRange,
                longitudeRange,
                scoringMetricIds,
                candidateSettlementIds,
            ).flatMap(::executeAnalysisQuery)

    private fun executeSettlementQuery(query: DatasetQuerySpec): List<Settlement> =
        database.rawQuery(query.sql, query.selectionArgs()).use(::readSettlements)

    private fun executeAnalysisQuery(query: DatasetQuerySpec): List<SettlementAnalysisCandidate> =
        database.rawQuery(query.sql, query.selectionArgs()).use(::readAnalysisCandidates)

    private fun DatasetQuerySpec.selectionArgs(): Array<String> =
        arguments.map { argument ->
            when (argument) {
                is DatasetQueryArgument.Text -> argument.value
                is DatasetQueryArgument.Real -> argument.value.toString()
                is DatasetQueryArgument.Integer -> argument.value.toString()
            }
        }.toTypedArray()

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

    private fun readPreferenceDirection(value: String): PreferredDirection =
        when (value) {
            "lower" -> PreferredDirection.LOWER
            "higher" -> PreferredDirection.HIGHER
            else -> error("Unsupported preference direction: $value")
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
