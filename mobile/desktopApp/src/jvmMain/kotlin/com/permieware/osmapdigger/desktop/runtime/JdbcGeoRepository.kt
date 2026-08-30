package com.permieware.osmapdigger.desktop.runtime

import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.dataset.DatasetCandidateQueries
import com.permieware.osmapdigger.dataset.DatasetQueryArgument
import com.permieware.osmapdigger.dataset.DatasetQuerySpec
import com.permieware.osmapdigger.dataset.GeoRepository
import com.permieware.osmapdigger.search.SettlementNameNormalizer
import java.sql.Connection
import java.sql.ResultSet

/** JDBC implementation over a generated OsmapDigger SQLite database. */
class JdbcGeoRepository(
    private val connection: Connection,
    private val info: DatasetInfo,
) : GeoRepository {
    override suspend fun datasetInfo(): DatasetInfo = info

    override suspend fun metricDefinitions(): List<MetricDefinition> =
        connection.prepareStatement(
            """
            SELECT metric_id, category_id, group_id, title, description, unit,
                   measure_type, preferred_direction, default_enabled, sort_order
            FROM metric_definition
            ORDER BY sort_order
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(readMetricDefinition(rows))
                    }
                }
            }
        }

    override suspend fun preferenceDefaults(): List<MetricPreferenceDefault> {
        if (!hasTable("metric_preference_default")) return emptyList()

        return connection.prepareStatement(
            """
            SELECT p.metric_id, p.direction, p.target_value, p.limit_value,
                   p.weight, p.default_enabled
            FROM metric_preference_default p
            JOIN metric_definition d ON d.metric_id = p.metric_id
            ORDER BY d.sort_order, p.metric_id
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            MetricPreferenceDefault(
                                metricId = rows.getString(1),
                                direction = readPreferenceDirection(rows.getString(2)),
                                targetValue = rows.getDouble(3),
                                limitValue = rows.getDouble(4),
                                weight = rows.getInt(5),
                                defaultEnabled = rows.getInt(6) != 0,
                            ),
                        )
                    }
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
        connection.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        ).use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { rows -> rows.next() }
        }

    private fun readPersistedSettlementSearchEntries(): List<SettlementSearchEntry> =
        connection.prepareStatement(
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
        ).use { statement ->
            statement.executeQuery().use { rows ->
                val entries = linkedMapOf<String, Pair<Settlement, MutableList<SettlementName>>>()
                while (rows.next()) {
                    val settlement = readSettlement(rows)
                    val pair = entries.getOrPut(settlement.id) { settlement to mutableListOf() }
                    rows.getString(9)?.let { name ->
                        pair.second +=
                            SettlementName(
                                value = name,
                                normalizedValue = rows.getString(10),
                                language = rows.getString(11),
                                kind = rows.getString(12),
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
        }

    private fun readLegacySettlementSearchEntries(): List<SettlementSearchEntry> =
        connection.prepareStatement(
            """
            SELECT settlement_id, name, name_local, name_en, place_type, population, latitude, longitude
            FROM settlement
            ORDER BY settlement_id
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val settlement = readSettlement(rows)
                        add(SettlementSearchEntry(settlement, legacyNames(settlement)))
                    }
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

    /** Execute the shared legacy hard-filter query through JDBC. */
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
     * Execute the shared ranked-analysis query specifications through JDBC.
     *
     * SQL semantics and deterministic candidate batching are owned by [DatasetCandidateQueries];
     * JDBC remains responsible only for typed binding, execution, and row mapping.
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
        connection.prepareStatement(query.sql).use { statement ->
            bindParameters(statement, query.arguments)
            statement.executeQuery().use(::readSettlements)
        }

    private fun executeAnalysisQuery(query: DatasetQuerySpec): List<SettlementAnalysisCandidate> =
        connection.prepareStatement(query.sql).use { statement ->
            bindParameters(statement, query.arguments)
            statement.executeQuery().use(::readAnalysisCandidates)
        }

    private fun bindParameters(
        statement: java.sql.PreparedStatement,
        parameters: List<DatasetQueryArgument>,
    ) {
        parameters.forEachIndexed { index, value ->
            when (value) {
                is DatasetQueryArgument.Text -> statement.setString(index + 1, value.value)
                is DatasetQueryArgument.Real -> statement.setDouble(index + 1, value.value)
                is DatasetQueryArgument.Integer -> statement.setInt(index + 1, value.value)
            }
        }
    }

    /** Hydrate one selected settlement together with every available metric value. */
    override suspend fun details(settlementId: String): SettlementDetails {
        val settlement =
            connection.prepareStatement(
                """
                SELECT settlement_id, name, name_local, name_en, place_type, population, latitude, longitude
                FROM settlement
                WHERE settlement_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, settlementId)
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "Settlement not found: $settlementId" }
                    readSettlement(rows)
                }
            }

        val metrics =
            connection.prepareStatement(
                """
                SELECT d.metric_id, d.category_id, d.group_id, d.title, d.description,
                       d.unit, d.measure_type, d.preferred_direction, d.default_enabled,
                       d.sort_order, m.value
                FROM settlement_metric m
                JOIN metric_definition d ON d.metric_id = m.metric_id
                WHERE m.settlement_id = ?
                ORDER BY d.sort_order
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, settlementId)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(MetricValue(readMetricDefinition(rows), rows.getDouble(11)))
                        }
                    }
                }
            }

        return SettlementDetails(settlement, metrics)
    }

    private fun readAnalysisCandidates(rows: ResultSet): List<SettlementAnalysisCandidate> {
        val candidates =
            linkedMapOf<String, Pair<Settlement, MutableMap<String, Double>>>()
        while (rows.next()) {
            val settlement = readSettlement(rows)
            val candidate =
                candidates.getOrPut(settlement.id) {
                    settlement to linkedMapOf()
                }
            rows.getString(9)?.let { metricId ->
                candidate.second[metricId] = rows.getDouble(10)
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

    private fun readMetricDefinition(rows: ResultSet): MetricDefinition =
        MetricDefinition(
            id = rows.getString(1),
            categoryId = rows.getString(2),
            group = rows.getString(3),
            title = rows.getString(4),
            description = rows.getString(5),
            unit = rows.getString(6),
            measureType = rows.getString(7),
            preferredDirection =
                when (rows.getString(8)) {
                    "higher" -> PreferredDirection.HIGHER
                    "neutral" -> PreferredDirection.NEUTRAL
                    else -> PreferredDirection.LOWER
                },
            defaultEnabled = rows.getInt(9) != 0,
            sortOrder = rows.getInt(10),
        )

    private fun readSettlements(rows: ResultSet): List<Settlement> =
        buildList {
            while (rows.next()) {
                add(readSettlement(rows))
            }
        }

    private fun readSettlement(rows: ResultSet): Settlement =
        Settlement(
            id = rows.getString(1),
            name = rows.getString(2),
            localName = rows.getString(3),
            englishName = rows.getString(4),
            placeType = rows.getString(5),
            population = rows.getLong(6).let { if (rows.wasNull()) null else it },
            location = GeoPoint(rows.getDouble(7), rows.getDouble(8)),
        )

}
