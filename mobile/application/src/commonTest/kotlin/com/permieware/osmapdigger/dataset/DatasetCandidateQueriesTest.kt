package com.permieware.osmapdigger.dataset

import com.permieware.osmapdigger.domain.SearchCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatasetCandidateQueriesTest {
    @Test
    fun searchQueryOwnsHardFilterRangeAndLimitSemantics() {
        val query =
            DatasetCandidateQueries.searchCandidates(
                conditions = listOf(SearchCondition("forest.distance_km", minValue = 1.0, maxValue = 5.0)),
                latitudeRange = 52.0..54.0,
                longitudeRange = 25.0..27.0,
                limit = 30,
            )

        assertEquals(
            listOf(
                DatasetQueryArgument.Real(52.0),
                DatasetQueryArgument.Real(54.0),
                DatasetQueryArgument.Real(25.0),
                DatasetQueryArgument.Real(27.0),
                DatasetQueryArgument.Text("forest.distance_km"),
                DatasetQueryArgument.Real(1.0),
                DatasetQueryArgument.Real(5.0),
                DatasetQueryArgument.Integer(30),
            ),
            query.arguments,
        )
        assertTrue(query.sql.contains("AND s.latitude BETWEEN ? AND ?"))
        assertTrue(query.sql.contains("AND s.longitude BETWEEN ? AND ?"))
        assertTrue(query.sql.contains("FROM settlement_metric sm0"))
        assertTrue(query.sql.endsWith("ORDER BY s.name\nLIMIT ?"))
    }

    @Test
    fun analysisQueryKeepsMissingScoringMetricsThroughLeftJoin() {
        val query =
            DatasetCandidateQueries.analysisCandidates(
                conditions = listOf(SearchCondition("water.distance_km", maxValue = 5.0)),
                latitudeRange = null,
                longitudeRange = null,
                scoringMetricIds = setOf("school.distance_km", "forest.distance_km"),
                candidateSettlementIds = setOf("c", "a"),
            ).single()

        assertTrue(query.sql.contains("LEFT JOIN settlement_metric sm_score"))
        assertTrue(query.sql.contains("sm_score.metric_id IN (?,?)"))
        assertTrue(query.sql.contains("s.settlement_id IN (?,?)"))
        assertEquals(
            listOf(
                DatasetQueryArgument.Text("forest.distance_km"),
                DatasetQueryArgument.Text("school.distance_km"),
                DatasetQueryArgument.Text("water.distance_km"),
                DatasetQueryArgument.Real(5.0),
                DatasetQueryArgument.Text("a"),
                DatasetQueryArgument.Text("c"),
            ),
            query.arguments,
        )
    }

    @Test
    fun analysisQueryWithoutScoringMetricsStillReturnsCandidateRows() {
        val query =
            DatasetCandidateQueries.analysisCandidates(
                conditions = emptyList(),
                latitudeRange = null,
                longitudeRange = null,
                scoringMetricIds = emptySet(),
                candidateSettlementIds = null,
            ).single()

        assertTrue(query.sql.contains("NULL AS scoring_metric_id"))
        assertTrue(query.arguments.isEmpty())
    }

    @Test
    fun emptyImportedScopeBuildsNoAnalysisQuery() {
        assertTrue(
            DatasetCandidateQueries.analysisCandidates(
                conditions = emptyList(),
                latitudeRange = null,
                longitudeRange = null,
                scoringMetricIds = emptySet(),
                candidateSettlementIds = emptySet(),
            ).isEmpty(),
        )
    }

    @Test
    fun largeImportedScopeIsPartitionedDeterministicallyWithinSqliteBindBudget() {
        val ids = (0 until 901).map { "id" + it.toString().padStart(4, '0') }.toSet()
        val queries =
            DatasetCandidateQueries.analysisCandidates(
                conditions = emptyList(),
                latitudeRange = null,
                longitudeRange = null,
                scoringMetricIds = emptySet(),
                candidateSettlementIds = ids,
            )

        assertEquals(2, queries.size)
        assertEquals(900, queries.first().arguments.size)
        assertEquals(DatasetQueryArgument.Text("id0000"), queries.first().arguments.first())
        assertEquals(DatasetQueryArgument.Text("id0899"), queries.first().arguments.last())
        assertEquals(listOf(DatasetQueryArgument.Text("id0900")), queries.last().arguments)
    }
}
