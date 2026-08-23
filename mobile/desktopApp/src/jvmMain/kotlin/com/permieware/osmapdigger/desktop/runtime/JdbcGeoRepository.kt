package com.permieware.osmapdigger.desktop.runtime

import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.runtime.GeoRepository
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

    override suspend fun findSettlements(query: String, limit: Int): List<Settlement> =
        connection.prepareStatement(
            """
            SELECT settlement_id, name, name_local, name_en, place_type, population, latitude, longitude
            FROM settlement
            WHERE lower(name) LIKE lower(?)
               OR lower(COALESCE(name_local, '')) LIKE lower(?)
               OR lower(COALESCE(name_en, '')) LIKE lower(?)
            ORDER BY CASE WHEN lower(name) = lower(?) THEN 0 ELSE 1 END, name
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            val pattern = "%$query%"
            statement.setString(1, pattern)
            statement.setString(2, pattern)
            statement.setString(3, pattern)
            statement.setString(4, query)
            statement.setInt(5, limit)
            statement.executeQuery().use(::readSettlements)
        }

    /**
     * Build the dynamic SQL candidate query from generic metric ranges.
     *
     * Each effective metric uses an EXISTS subquery. Consequently a missing metric row
     * does not behave like zero and cannot satisfy the requested condition. Geographic
     * ranges are only a coarse radius reduction; shared SearchService applies exact
     * Haversine filtering after this method returns.
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
        val parameters = mutableListOf<Any>()

        latitudeRange?.let {
            sql.append("\nAND s.latitude BETWEEN ? AND ?")
            parameters += it.start
            parameters += it.endInclusive
        }
        longitudeRange?.let {
            sql.append("\nAND s.longitude BETWEEN ? AND ?")
            parameters += it.start
            parameters += it.endInclusive
        }

        conditions.forEachIndexed { index, condition ->
            sql.append(
                """

                AND EXISTS (
                    SELECT 1
                    FROM settlement_metric sm$index
                    WHERE sm$index.settlement_id = s.settlement_id
                      AND sm$index.metric_id = ?
                """.trimIndent(),
            )
            parameters += condition.metricId
            condition.minValue?.let {
                sql.append("\n  AND sm$index.value >= ?")
                parameters += it
            }
            condition.maxValue?.let {
                sql.append("\n  AND sm$index.value <= ?")
                parameters += it
            }
            sql.append("\n)")
        }

        sql.append("\nORDER BY s.name\nLIMIT ?")
        parameters += limit

        return connection.prepareStatement(sql.toString()).use { statement ->
            parameters.forEachIndexed { index, value ->
                when (value) {
                    is String -> statement.setString(index + 1, value)
                    is Double -> statement.setDouble(index + 1, value)
                    is Int -> statement.setInt(index + 1, value)
                    else -> statement.setObject(index + 1, value)
                }
            }
            statement.executeQuery().use(::readSettlements)
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
