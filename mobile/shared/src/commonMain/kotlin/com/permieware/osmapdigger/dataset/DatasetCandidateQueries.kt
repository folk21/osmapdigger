package com.permieware.osmapdigger.dataset

import com.permieware.osmapdigger.domain.SearchCondition

/** Typed bind values for platform-independent SQLite query specifications. */
sealed interface DatasetQueryArgument {
    data class Text(val value: String) : DatasetQueryArgument

    data class Real(val value: Double) : DatasetQueryArgument

    data class Integer(val value: Int) : DatasetQueryArgument
}

/** SQL plus ordered bind arguments whose semantics are shared by Android and Desktop adapters. */
data class DatasetQuerySpec(
    val sql: String,
    val arguments: List<DatasetQueryArgument>,
)

/**
 * Builds compatibility-sensitive settlement candidate SQL used by both runtime SQLite adapters.
 *
 * This owner deliberately stops at declarative query semantics. JDBC/Android execution, binding,
 * cursor/result mapping, connection ownership, and diagnostics remain platform responsibilities.
 */
object DatasetCandidateQueries {
    private const val SQLITE_SAFE_BIND_PARAMETER_COUNT: Int = 900

    /** Preserve the legacy hard-filter path, including name ordering and repository-side limit. */
    fun searchCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        limit: Int,
    ): DatasetQuerySpec {
        val sql =
            StringBuilder(
                """
                SELECT s.settlement_id, s.name, s.name_local, s.name_en, s.place_type,
                       s.population, s.latitude, s.longitude
                FROM settlement s
                WHERE 1 = 1
                """.trimIndent(),
            )
        val arguments = mutableListOf<DatasetQueryArgument>()
        appendCandidatePredicates(sql, arguments, conditions, latitudeRange, longitudeRange)
        sql.append("\nORDER BY s.name\nLIMIT ?")
        arguments += DatasetQueryArgument.Integer(limit)
        return DatasetQuerySpec(sql.toString(), arguments)
    }

    /**
     * Build one or more bounded ranked-analysis queries.
     *
     * A null candidate-ID set means the whole dataset. An explicit empty set produces no query and
     * therefore no candidates. Requested scoring metrics are LEFT JOINed so missing values remain
     * unknown rather than becoming zero. Candidate IDs and metric IDs are sorted before query
     * construction to keep batching and bind order deterministic across platforms.
     */
    fun analysisCandidates(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        scoringMetricIds: Set<String>,
        candidateSettlementIds: Set<String>?,
    ): List<DatasetQuerySpec> {
        require(scoringMetricIds.none { it.isBlank() }) { "Scoring metric IDs must not be blank" }
        require(candidateSettlementIds == null || candidateSettlementIds.none { it.isBlank() }) {
            "Candidate settlement IDs must not be blank"
        }
        if (candidateSettlementIds != null && candidateSettlementIds.isEmpty()) return emptyList()

        val metricIds = scoringMetricIds.sorted()
        val candidateBatches =
            candidateSettlementIds?.let { ids ->
                val fixedParameterCount =
                    metricIds.size +
                        rangeParameterCount(latitudeRange) +
                        rangeParameterCount(longitudeRange) +
                        conditionParameterCount(conditions)
                val maxBatchSize = SQLITE_SAFE_BIND_PARAMETER_COUNT - fixedParameterCount
                require(maxBatchSize > 0) {
                    "Analysis query leaves no SQLite bind parameters for candidate settlement IDs"
                }
                StableIdBatches.partition(ids, maxBatchSize)
            }

        return if (candidateBatches == null) {
            listOf(
                analysisCandidateBatch(
                    conditions,
                    latitudeRange,
                    longitudeRange,
                    metricIds,
                    candidateSettlementIds = null,
                ),
            )
        } else {
            candidateBatches.map { batch ->
                analysisCandidateBatch(
                    conditions,
                    latitudeRange,
                    longitudeRange,
                    metricIds,
                    candidateSettlementIds = batch,
                )
            }
        }
    }

    private fun analysisCandidateBatch(
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
        metricIds: List<String>,
        candidateSettlementIds: List<String>?,
    ): DatasetQuerySpec {
        val arguments = mutableListOf<DatasetQueryArgument>()
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
                arguments += metricIds.map(DatasetQueryArgument::Text)
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

        appendCandidatePredicates(sql, arguments, conditions, latitudeRange, longitudeRange)
        candidateSettlementIds?.let { ids ->
            sql.append("\nAND s.settlement_id IN (${ids.joinToString(",") { "?" }})")
            arguments += ids.map(DatasetQueryArgument::Text)
        }
        sql.append("\nORDER BY s.settlement_id, scoring_metric_id")
        return DatasetQuerySpec(sql.toString(), arguments)
    }

    private fun rangeParameterCount(range: ClosedFloatingPointRange<Double>?): Int =
        if (range == null) 0 else 2

    private fun conditionParameterCount(conditions: List<SearchCondition>): Int =
        conditions.sumOf { condition ->
            1 + (if (condition.minValue == null) 0 else 1) + (if (condition.maxValue == null) 0 else 1)
        }

    private fun appendCandidatePredicates(
        sql: StringBuilder,
        arguments: MutableList<DatasetQueryArgument>,
        conditions: List<SearchCondition>,
        latitudeRange: ClosedFloatingPointRange<Double>?,
        longitudeRange: ClosedFloatingPointRange<Double>?,
    ) {
        latitudeRange?.let {
            sql.append("\nAND s.latitude BETWEEN ? AND ?")
            arguments += DatasetQueryArgument.Real(it.start)
            arguments += DatasetQueryArgument.Real(it.endInclusive)
        }
        longitudeRange?.let {
            sql.append("\nAND s.longitude BETWEEN ? AND ?")
            arguments += DatasetQueryArgument.Real(it.start)
            arguments += DatasetQueryArgument.Real(it.endInclusive)
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
            arguments += DatasetQueryArgument.Text(condition.metricId)
            condition.minValue?.let {
                sql.append("\n  AND sm$index.value >= ?")
                arguments += DatasetQueryArgument.Real(it)
            }
            condition.maxValue?.let {
                sql.append("\n  AND sm$index.value <= ?")
                arguments += DatasetQueryArgument.Real(it)
            }
            sql.append("\n)")
        }
    }
}
