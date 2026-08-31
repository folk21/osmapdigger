package com.permieware.osmapdigger.desktop.settings

import com.permieware.osmapdigger.settings.ApplicationSettingsSchema
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


private val testSeeds =
    listOf(
        com.permieware.osmapdigger.external.ExternalSearchProvider(
            id = "google",
            title = "Google",
            countryCode = null,
            urlTemplate = "https://www.google.com/search?q={query}",
            priority = 10,
        ),
        com.permieware.osmapdigger.external.ExternalSearchProvider(
            id = "yandex",
            title = "Yandex",
            countryCode = null,
            urlTemplate = "https://yandex.com/search/?text={query}",
            priority = 20,
        ),
        com.permieware.osmapdigger.external.ExternalSearchProvider(
            id = "kufar-by",
            title = "Kufar",
            countryCode = "BY",
            urlTemplate = "https://www.google.com/search?q=site%3Are.kufar.by%20{query}",
            priority = 100,
        ),
    )

class SqliteExternalSearchProviderRepositoryTest {
    @Test
    fun globalAndCountryProvidersAreReturnedInPriorityOrder() = runTest {
        val path = Files.createTempDirectory("osmapdigger-providers-").resolve("settings.sqlite")
        try {
            val providers = SqliteExternalSearchProviderRepository(path, testSeeds).providersFor("BY")

            assertEquals(listOf("google", "yandex", "kufar-by"), providers.map { it.id })
        } finally {
            path.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun seededDefaultsDoNotOverwriteCustomizedRows() = runTest {
        val path = Files.createTempDirectory("osmapdigger-providers-").resolve("settings.sqlite")
        try {
            val repository = SqliteExternalSearchProviderRepository(path, testSeeds)
            repository.providersFor("BY")

            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
                connection.prepareStatement(
                    """
                    UPDATE external_search_provider
                    SET title = 'My Google', url_template = 'https://example.test/?q={query}'
                    WHERE provider_id = 'google' AND country_code = '*'
                    """.trimIndent(),
                ).use { it.executeUpdate() }
            }

            val google = SqliteExternalSearchProviderRepository(path, testSeeds).providersFor("BY").first()
            assertEquals("My Google", google.title)
            assertEquals("https://example.test/?q={query}", google.urlTemplate)
        } finally {
            path.parent.toFile().deleteRecursively()
        }
    }


    @Test
    fun customCountryProviderIsReturnedWithoutUiCodeChanges() = runTest {
        val path = Files.createTempDirectory("osmapdigger-providers-").resolve("settings.sqlite")
        try {
            val repository = SqliteExternalSearchProviderRepository(path, testSeeds)
            repository.providersFor("BY")

            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO external_search_provider(
                        provider_id, title, country_code, url_template, enabled, priority
                    ) VALUES ('custom-by', 'Custom BY', 'BY', 'https://example.test/?q={query}', 1, 50)
                    """.trimIndent(),
                ).use { it.executeUpdate() }
            }

            val providers = SqliteExternalSearchProviderRepository(path, testSeeds).providersFor("BY")
            assertEquals(
                listOf("google", "yandex", "custom-by", "kufar-by"),
                providers.map { it.id },
            )
        } finally {
            path.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun providerTermsOverridePersistsCustomAndExplicitEmptyValues() = runTest {
        val path = Files.createTempDirectory("osmapdigger-providers-").resolve("settings.sqlite")
        try {
            val repository = SqliteExternalSearchProviderRepository(path, testSeeds)
            assertEquals(null, repository.providersFor("BY").first { it.id == "kufar-by" }.queryTermsOverride)

            repository.updateQueryTerms(
                listOf(
                    ExternalSearchProviderTermsUpdate(
                        providerId = "google",
                        countryCode = null,
                        queryTermsOverride = "house land",
                    ),
                    ExternalSearchProviderTermsUpdate(
                        providerId = "kufar-by",
                        countryCode = "BY",
                        queryTermsOverride = "",
                    ),
                ),
            )

            val providers = SqliteExternalSearchProviderRepository(path, testSeeds).providersFor("BY").associateBy { it.id }
            assertEquals("house land", providers.getValue("google").queryTermsOverride)
            assertEquals("", providers.getValue("kufar-by").queryTermsOverride)
        } finally {
            path.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun versionOnePreferencesDatabaseMigratesToCurrentSchemaWithoutLosingPreferences() = runTest {
        val path = Files.createTempDirectory("osmapdigger-providers-").resolve("settings.sqlite")
        try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        CREATE TABLE user_preferences (
                            id INTEGER PRIMARY KEY NOT NULL CHECK(id = 1),
                            dataset_id TEXT NOT NULL,
                            center_settlement_id TEXT,
                            center_settlement_name TEXT,
                            radius_km REAL,
                            filters_json TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        "INSERT INTO user_preferences(id, dataset_id, filters_json) VALUES (1, 'andorra', '{\"version\":1,\"conditions\":[]}')",
                    )
                    statement.execute("PRAGMA user_version = 1")
                }
            }

            SqliteExternalSearchProviderRepository(path, testSeeds).providersFor("AD")

            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
                assertEquals(
                    ApplicationSettingsSchema.VERSION,
                    connection.createStatement().use { statement ->
                        statement.executeQuery("PRAGMA user_version").use { result ->
                            result.next()
                            result.getInt(1)
                        }
                    },
                )
                assertEquals(
                    "andorra",
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT dataset_id FROM user_preferences WHERE id = 1").use { result ->
                            assertTrue(result.next())
                            result.getString(1)
                        }
                    },
                )
                val columns =
                    connection.createStatement().use { statement ->
                        statement.executeQuery("PRAGMA table_info(user_preferences)").use { result ->
                            buildSet {
                                while (result.next()) {
                                    add(result.getString("name"))
                                }
                            }
                        }
                    }
                assertTrue("preferences_json" in columns)
                assertTrue("candidate_scope_json" in columns)
                val providerColumns =
                    connection.createStatement().use { statement ->
                        statement.executeQuery("PRAGMA table_info(external_search_provider)").use { result ->
                            buildSet {
                                while (result.next()) add(result.getString("name"))
                            }
                        }
                    }
                assertTrue("query_terms_override" in providerColumns)
            }
        } finally {
            path.parent.toFile().deleteRecursively()
        }
    }
}
