package com.permieware.osmapdigger.desktop.runtime

import com.permieware.osmapdigger.domain.*
import kotlinx.coroutines.runBlocking
import org.sqlite.SQLiteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JdbcGeoRepositoryTest {
    @Test
    fun filtersByDynamicMetric() = runBlocking {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE settlement(settlement_id TEXT, name TEXT, name_local TEXT, name_en TEXT, place_type TEXT, population INTEGER, latitude REAL, longitude REAL)",
                )
                statement.execute(
                    "CREATE TABLE metric_definition(metric_id TEXT, category_id TEXT, group_id TEXT, title TEXT, description TEXT, unit TEXT, measure_type TEXT, preferred_direction TEXT, default_enabled INTEGER, sort_order INTEGER)",
                )
                statement.execute(
                    "CREATE TABLE settlement_metric(settlement_id TEXT, metric_id TEXT, value REAL)",
                )
                statement.execute("INSERT INTO settlement VALUES ('1','A',NULL,NULL,'village',NULL,42.5,1.5)")
                statement.execute("INSERT INTO settlement_metric VALUES ('1','water.distance_km',2.0)")
            }

            val repository =
                JdbcGeoRepository(
                    connection,
                    DatasetInfo("test", "Test", null, GeoPoint(42.5, 1.5), 10.0, false, null, "property"),
                )

            val result =
                repository.searchCandidates(
                    conditions = listOf(SearchCondition("water.distance_km", maxValue = 5.0)),
                    latitudeRange = null,
                    longitudeRange = null,
                    limit = 10,
                )

            assertEquals(listOf("A"), result.map { it.name })
        }
    }

    @Test
    fun analysisCandidatesBatchLoadsOnlyRequestedMetricsAndKeepsUnknowns() = runBlocking {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                createAnalysisTables(statement)
                statement.execute(
                    "INSERT INTO settlement VALUES ('a','Alpha',NULL,NULL,'village',NULL,42.5,1.5)",
                )
                statement.execute(
                    "INSERT INTO settlement VALUES ('b','Beta',NULL,NULL,'village',NULL,42.6,1.6)",
                )
                statement.execute(
                    "INSERT INTO settlement VALUES ('c','Gamma',NULL,NULL,'village',NULL,42.7,1.7)",
                )
                statement.execute("INSERT INTO settlement_metric VALUES ('a','water.distance_km',2.0)")
                statement.execute("INSERT INTO settlement_metric VALUES ('a','forest.distance_km',1.0)")
                statement.execute("INSERT INTO settlement_metric VALUES ('a','school.distance_km',3.0)")
                statement.execute("INSERT INTO settlement_metric VALUES ('b','water.distance_km',4.0)")
                statement.execute("INSERT INTO settlement_metric VALUES ('b','school.distance_km',2.0)")
                statement.execute("INSERT INTO settlement_metric VALUES ('c','water.distance_km',8.0)")
                statement.execute("INSERT INTO settlement_metric VALUES ('c','forest.distance_km',0.5)")
            }

            val result =
                repository(connection).analysisCandidates(
                    conditions = listOf(SearchCondition("water.distance_km", maxValue = 5.0)),
                    latitudeRange = null,
                    longitudeRange = null,
                    scoringMetricIds = setOf("forest.distance_km", "school.distance_km"),
                )

            assertEquals(listOf("a", "b"), result.map { it.settlement.id })
            assertEquals(
                mapOf(
                    "forest.distance_km" to 1.0,
                    "school.distance_km" to 3.0,
                ),
                result[0].metricValues,
            )
            assertEquals(mapOf("school.distance_km" to 2.0), result[1].metricValues)
            assertTrue(result.none { "water.distance_km" in it.metricValues })
        }
    }

    @Test
    fun analysisCandidatesSupportsNoScoringMetricsWithoutDroppingEligibleSettlements() = runBlocking {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                createAnalysisTables(statement)
                statement.execute(
                    "INSERT INTO settlement VALUES ('a','Alpha',NULL,NULL,'village',NULL,42.5,1.5)",
                )
                statement.execute(
                    "INSERT INTO settlement VALUES ('b','Beta',NULL,NULL,'village',NULL,42.6,1.6)",
                )
            }

            val result =
                repository(connection).analysisCandidates(
                    conditions = emptyList(),
                    latitudeRange = null,
                    longitudeRange = null,
                    scoringMetricIds = emptySet(),
                )

            assertEquals(listOf("a", "b"), result.map { it.settlement.id })
            assertTrue(result.all { it.metricValues.isEmpty() })
        }
    }


    @Test
    fun readsPersistedPreferenceDefaultsInMetricOrder() = runBlocking {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                createAnalysisTables(statement)
                statement.execute(
                    """
                    CREATE TABLE metric_preference_default(
                        metric_id TEXT, direction TEXT, target_value REAL, limit_value REAL,
                        weight INTEGER, default_enabled INTEGER
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO metric_definition VALUES (
                        'water.distance_km','water','Nature','Water','Water','km',
                        'distance','lower',1,20
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO metric_definition VALUES (
                        'landfill.distance_km','landfill','Risks','Landfill','Landfill','km',
                        'distance','higher',1,10
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO metric_preference_default VALUES (
                        'water.distance_km','lower',2.0,15.0,8,1
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO metric_preference_default VALUES (
                        'landfill.distance_km','higher',15.0,3.0,9,0
                    )
                    """.trimIndent(),
                )
            }

            val defaults = repository(connection).preferenceDefaults()

            assertEquals(
                listOf("landfill.distance_km", "water.distance_km"),
                defaults.map { it.metricId },
            )
            assertEquals(PreferredDirection.HIGHER, defaults[0].direction)
            assertEquals(15.0, defaults[0].targetValue)
            assertEquals(3.0, defaults[0].limitValue)
            assertEquals(9, defaults[0].weight)
            assertEquals(false, defaults[0].defaultEnabled)
            assertEquals(PreferredDirection.LOWER, defaults[1].direction)
            assertEquals(true, defaults[1].defaultEnabled)
        }
    }

    @Test
    fun legacyV1WithoutPreferenceDefaultTableReturnsNoDefaults() = runBlocking {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }
        dataSource.connection.use { connection ->
            connection.createStatement().use(::createAnalysisTables)

            assertTrue(repository(connection).preferenceDefaults().isEmpty())
        }
    }

    @Test
    fun readsPersistedMultilingualSettlementNames() = runBlocking {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE settlement(settlement_id TEXT, name TEXT, name_local TEXT, name_en TEXT, place_type TEXT, population INTEGER, latitude REAL, longitude REAL)",
                )
                statement.execute(
                    "CREATE TABLE settlement_name(settlement_id TEXT, name TEXT, normalized_name TEXT, language TEXT, kind TEXT)",
                )
                statement.execute(
                    "CREATE TABLE metric_definition(metric_id TEXT, category_id TEXT, group_id TEXT, title TEXT, description TEXT, unit TEXT, measure_type TEXT, preferred_direction TEXT, default_enabled INTEGER, sort_order INTEGER)",
                )
                statement.execute(
                    "CREATE TABLE settlement_metric(settlement_id TEXT, metric_id TEXT, value REAL)",
                )
                statement.execute(
                    "INSERT INTO settlement VALUES ('node:1','Віцебск','Віцебск','Vitebsk','city',366299,55.1904,30.2049)",
                )
                statement.execute(
                    "INSERT INTO settlement_name VALUES ('node:1','Віцебск','віцебск',NULL,'primary')",
                )
                statement.execute(
                    "INSERT INTO settlement_name VALUES ('node:1','Витебск','витебск','ru','localized')",
                )
                statement.execute(
                    "INSERT INTO settlement_name VALUES ('node:1','Vitebsk','vitebsk','en','localized')",
                )
            }

            val entries = repository(connection).settlementSearchEntries()

            assertEquals(1, entries.size)
            assertEquals(listOf("Віцебск", "Vitebsk", "Витебск"), entries.single().names.map { it.value })
        }
    }

    @Test
    fun buildsLegacySearchNamesWhenAliasTableIsAbsent() = runBlocking {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE settlement(settlement_id TEXT, name TEXT, name_local TEXT, name_en TEXT, place_type TEXT, population INTEGER, latitude REAL, longitude REAL)",
                )
                statement.execute(
                    "CREATE TABLE metric_definition(metric_id TEXT, category_id TEXT, group_id TEXT, title TEXT, description TEXT, unit TEXT, measure_type TEXT, preferred_direction TEXT, default_enabled INTEGER, sort_order INTEGER)",
                )
                statement.execute(
                    "CREATE TABLE settlement_metric(settlement_id TEXT, metric_id TEXT, value REAL)",
                )
                statement.execute(
                    "INSERT INTO settlement VALUES ('node:1','Віцебск','Віцебск','Vitebsk','city',366299,55.1904,30.2049)",
                )
            }

            val entry = repository(connection).settlementSearchEntries().single()

            assertEquals(listOf("Віцебск", "Vitebsk"), entry.names.map { it.value })
            assertTrue(entry.names.all { it.normalizedValue.isNotBlank() })
        }
    }

    private fun createAnalysisTables(statement: java.sql.Statement) {
        statement.execute(
            "CREATE TABLE settlement(settlement_id TEXT, name TEXT, name_local TEXT, name_en TEXT, place_type TEXT, population INTEGER, latitude REAL, longitude REAL)",
        )
        statement.execute(
            "CREATE TABLE metric_definition(metric_id TEXT, category_id TEXT, group_id TEXT, title TEXT, description TEXT, unit TEXT, measure_type TEXT, preferred_direction TEXT, default_enabled INTEGER, sort_order INTEGER)",
        )
        statement.execute(
            "CREATE TABLE settlement_metric(settlement_id TEXT, metric_id TEXT, value REAL)",
        )
    }

    private fun repository(connection: java.sql.Connection) =
        JdbcGeoRepository(
            connection,
            DatasetInfo("test", "Test", null, GeoPoint(42.5, 1.5), 10.0, false, null, "property"),
        )

}
