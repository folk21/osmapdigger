package com.permieware.osmapdigger.desktop.headless

import com.permieware.osmapdigger.analysis.SettlementCandidateScope
import com.permieware.osmapdigger.desktop.runtime.JdbcGeoRepository
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.domain.SearchCondition
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Real JDBC + real Desktop settings SQLite acceptance for the shared shortlist application controller. */
class DesktopHeadlessWorkspaceAcceptanceTest {
    @Test
    fun shortlistWorkflowPersistsAcrossHeadlessRestart() = runBlocking {
        val root = Files.createTempDirectory("osmapdigger-headless-acceptance-")
        val datasetDatabase = root.resolve("georisk.sqlite")
        val settingsDatabase = root.resolve("settings.sqlite")
        val exportPath = root.resolve("favorites.zip")
        createDataset(datasetDatabase)
        var copiedSettlementIds = emptyList<String>()

        openWorkspace(datasetDatabase, settingsDatabase).use { workspace ->
            val imported = workspace.importCandidates("Альфа\nБета\nГамма")
            assertTrue(imported.applied)
            assertEquals(listOf("alpha", "beta", "gamma"), imported.resolvedSettlementIds)

            workspace.setRequiredCondition(SearchCondition("landfill.distance_km", minValue = 1.0))
            workspace.setPreferenceEnabled("forest.distance_km", true)
            workspace.setPreferenceEnabled("industrial.distance_km", true)
            workspace.setPreferenceEnabled("medical.distance_km", true)

            val ranked = workspace.state()
            assertTrue(ranked.candidateScope is SettlementCandidateScope.Imported)
            assertEquals(setOf("alpha", "beta", "gamma"), ranked.rankedCandidatesBySettlementId.keys)
            assertEquals("alpha", ranked.rankedResults.first().settlement.id)
            assertTrue(ranked.rankedCandidatesBySettlementId.getValue("gamma").result.score.coverage < 100.0)

            workspace.addFavorite("alpha")
            workspace.addFavorite("gamma")
            workspace.updateFavoriteNote("alpha", "Inspect road access")
            assertNotNull(workspace.state().favorites.first { it.settlementId == "alpha" }.analysisSnapshot)

            workspace.setRequiredCondition(SearchCondition("landfill.distance_km", minValue = 5.0))
            val afterRestriction = workspace.state()
            assertFalse("gamma" in afterRestriction.rankedCandidatesBySettlementId)
            assertTrue(afterRestriction.favorites.any { it.settlementId == "gamma" })

            workspace.updateFavoriteSnapshot("alpha")
            assertEquals(5.0, workspace.state().favorites.first { it.settlementId == "alpha" }.analysisSnapshot
                ?.requiredCriteria?.first { it.metricId == "landfill.distance_km" }?.minValue)

            val copied = workspace.copyFavoritesToImported(setOf("alpha", "gamma"))
            copiedSettlementIds = copied.settlementIds
            assertEquals(setOf("alpha", "gamma"), copiedSettlementIds.toSet())

            val batch = workspace.buildBatchSearch("google", setOf("alpha", "gamma"))
            assertTrue(batch.isNotEmpty())
            assertTrue(batch.single().url.contains("%D0%90%D0%BB%D1%8C%D1%84%D0%B0"))
            assertTrue(batch.single().url.contains("%D0%93%D0%B0%D0%BC%D0%BC%D0%B0"))

            workspace.exportFavorites(exportPath)
            ZipFile(exportPath.toFile()).use { zip ->
                assertEquals(listOf("favorites.json", "favorites.md"), zip.entries().asSequence().map { it.name }.toList())
                val manifest = zip.getInputStream(zip.getEntry("favorites.json")).bufferedReader().use { it.readText() }
                assertTrue(manifest.contains("Inspect road access"))
                assertTrue(manifest.contains("\"settlementId\": \"gamma\""))
            }
        }

        openWorkspace(datasetDatabase, settingsDatabase).use { reopened ->
            val state = reopened.state()
            assertEquals(copiedSettlementIds, (state.candidateScope as SettlementCandidateScope.Imported).settlementIds)
            assertEquals("Inspect road access", state.favorites.first { it.settlementId == "alpha" }.note)
            assertTrue(state.favorites.any { it.settlementId == "gamma" })
            assertEquals(5.0, state.conditions.first { it.metricId == "landfill.distance_km" }.minValue)
            assertTrue(state.effectivePreferences.filter { it.enabled }.map { it.metricId }.containsAll(
                listOf("forest.distance_km", "industrial.distance_km", "medical.distance_km"),
            ))
        }

        DriverManager.getConnection("jdbc:sqlite:${settingsDatabase.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM favorite_settlement").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(2, rows.getInt(1))
                }
                statement.executeQuery("SELECT note_text FROM favorite_settlement WHERE settlement_id = 'alpha'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Inspect road access", rows.getString(1))
                }
                statement.executeQuery("SELECT candidate_scope_json FROM user_preferences WHERE id = 1").use { rows ->
                    assertTrue(rows.next())
                    assertTrue(rows.getString(1).contains("alpha"))
                    assertTrue(rows.getString(1).contains("gamma"))
                }
            }
        }
    }

    private suspend fun openWorkspace(datasetDatabase: Path, settingsDatabase: Path): DesktopHeadlessWorkspace {
        Class.forName("org.sqlite.JDBC")
        val connection = DriverManager.getConnection("jdbc:sqlite:${datasetDatabase.toAbsolutePath()}")
        val repository = JdbcGeoRepository(connection, DATASET_INFO)
        return DesktopHeadlessWorkspace(repository, settingsDatabase, Closeable { connection.close() }).also { it.initialize() }
    }

    private fun createDataset(path: Path) {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE settlement(settlement_id TEXT PRIMARY KEY, name TEXT, name_local TEXT, name_en TEXT, place_type TEXT, population INTEGER, latitude REAL, longitude REAL)")
                statement.execute("CREATE TABLE metric_definition(metric_id TEXT PRIMARY KEY, category_id TEXT, group_id TEXT, title TEXT, description TEXT, unit TEXT, measure_type TEXT, preferred_direction TEXT, default_enabled INTEGER, sort_order INTEGER)")
                statement.execute("CREATE TABLE settlement_metric(settlement_id TEXT, metric_id TEXT, value REAL, PRIMARY KEY(settlement_id, metric_id))")
                statement.execute("CREATE TABLE metric_preference_default(metric_id TEXT PRIMARY KEY, direction TEXT, target_value REAL, limit_value REAL, weight INTEGER, default_enabled INTEGER)")

                insertMetric(statement, "forest.distance_km", "forest", "Nature", "Forest distance", "lower", 10)
                insertMetric(statement, "industrial.distance_km", "industrial", "Risks", "Industrial distance", "higher", 20)
                insertMetric(statement, "medical.distance_km", "medical", "Infrastructure", "Medical distance", "lower", 30)
                insertMetric(statement, "landfill.distance_km", "landfill", "Risks", "Landfill distance", "higher", 40)

                statement.execute("INSERT INTO metric_preference_default VALUES ('forest.distance_km','lower',0.5,3.0,5,1)")
                statement.execute("INSERT INTO metric_preference_default VALUES ('industrial.distance_km','higher',10.0,2.0,5,1)")
                statement.execute("INSERT INTO metric_preference_default VALUES ('medical.distance_km','lower',2.0,10.0,5,1)")

                insertSettlement(statement, "alpha", "Альфа", 0.010)
                insertSettlement(statement, "beta", "Бета", 0.020)
                insertSettlement(statement, "gamma", "Гамма", 0.030)
                insertSettlement(statement, "best", "Идеальная", 0.005)

                insertValues(statement, "alpha", forest = 0.2, industrial = 10.0, medical = 1.0, landfill = 10.0)
                insertValues(statement, "beta", forest = 1.5, industrial = 7.0, medical = 5.0, landfill = 8.0)
                insertValues(statement, "gamma", forest = 2.0, industrial = 5.0, medical = null, landfill = 2.0)
                insertValues(statement, "best", forest = 0.1, industrial = 12.0, medical = 0.5, landfill = 20.0)
            }
        }
    }

    private fun insertMetric(statement: java.sql.Statement, id: String, category: String, group: String, title: String, direction: String, order: Int) {
        statement.execute("INSERT INTO metric_definition VALUES ('$id','$category','$group','$title','$title','km','distance','$direction',0,$order)")
    }

    private fun insertSettlement(statement: java.sql.Statement, id: String, name: String, latitude: Double) {
        statement.execute("INSERT INTO settlement VALUES ('$id','$name','$name',NULL,'village',NULL,$latitude,0.0)")
    }

    private fun insertValues(statement: java.sql.Statement, id: String, forest: Double, industrial: Double, medical: Double?, landfill: Double) {
        statement.execute("INSERT INTO settlement_metric VALUES ('$id','forest.distance_km',$forest)")
        statement.execute("INSERT INTO settlement_metric VALUES ('$id','industrial.distance_km',$industrial)")
        medical?.let { statement.execute("INSERT INTO settlement_metric VALUES ('$id','medical.distance_km',$it)") }
        statement.execute("INSERT INTO settlement_metric VALUES ('$id','landfill.distance_km',$landfill)")
    }

    private companion object {
        val DATASET_INFO = DatasetInfo(
            id = "headless-acceptance",
            displayName = "Headless acceptance",
            countryCode = "BY",
            center = GeoPoint(0.0, 0.0),
            initialZoom = 10.0,
            hasMap = false,
            propertySearchSite = null,
            propertySearchTerms = "property",
        )
    }
}
