package com.permieware.osmapdigger.desktop.runtime

import com.permieware.osmapdigger.domain.*
import kotlinx.coroutines.runBlocking
import org.sqlite.SQLiteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
