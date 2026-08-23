package com.permieware.osmapdigger.runtime

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.permieware.osmapdigger.domain.*

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

    override suspend fun findSettlements(query: String, limit: Int): List<Settlement> {
        val pattern = "%$query%"
        return database.rawQuery(
            """
            SELECT settlement_id, name, name_local, name_en, place_type, population, latitude, longitude
            FROM settlement
            WHERE lower(name) LIKE lower(?)
               OR lower(COALESCE(name_local, '')) LIKE lower(?)
               OR lower(COALESCE(name_en, '')) LIKE lower(?)
            ORDER BY name
            LIMIT ?
            """.trimIndent(),
            arrayOf(pattern, pattern, pattern, limit.toString()),
        ).use(::readSettlements)
    }

    /**
     * Apply generic metric ranges and optional coordinate bounds using Android SQLite.
     * The SQL mirrors the Desktop EXISTS semantics so missing metric rows are unknown,
     * not zero, and shared SearchService can behave consistently on both platforms.
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

        sql.append("\nORDER BY s.name LIMIT ?")
        args += limit.toString()
        return database.rawQuery(sql.toString(), args.toTypedArray()).use(::readSettlements)
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
