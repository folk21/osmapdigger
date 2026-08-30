package com.permieware.osmapdigger.desktop.settings

import com.permieware.osmapdigger.settings.ApplicationSettingsSchema

import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/** Desktop SQLite adapter for configurable external settlement-search providers. */
class SqliteExternalSearchProviderRepository(
    databasePath: Path,
    private val seedProviders: List<ExternalSearchProvider>,
) : ExternalSearchProviderRepository {
    private val database = DesktopSettingsDatabase(databasePath)

    override suspend fun providersFor(countryCode: String?): List<ExternalSearchProvider> =
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openConnection().use { connection ->
                seedDefaults(connection)
                connection.prepareStatement(
                    """
                    SELECT provider_id, title, country_code, url_template, priority
                    FROM external_search_provider
                    WHERE enabled = 1
                      AND (country_code = ? OR country_code = ?)
                    ORDER BY priority, title, provider_id
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, ApplicationSettingsSchema.GLOBAL_COUNTRY_CODE)
                    statement.setString(2, countryCode?.uppercase() ?: "")
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                val storedCountry = result.getString("country_code")
                                add(
                                    ExternalSearchProvider(
                                        id = result.getString("provider_id"),
                                        title = result.getString("title"),
                                        countryCode = storedCountry.takeUnless {
                                            it == ApplicationSettingsSchema.GLOBAL_COUNTRY_CODE
                                        },
                                        urlTemplate = result.getString("url_template"),
                                        priority = result.getInt("priority"),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

    private fun seedDefaults(connection: java.sql.Connection) {
        connection.prepareStatement(
            """
            INSERT OR IGNORE INTO external_search_provider(
                provider_id, title, country_code, url_template, enabled, priority
            ) VALUES (?, ?, ?, ?, 1, ?)
            """.trimIndent(),
        ).use { statement ->
            seedProviders.forEach { provider ->
                statement.setString(1, provider.id)
                statement.setString(2, provider.title)
                statement.setString(
                    3,
                    provider.countryCode ?: ApplicationSettingsSchema.GLOBAL_COUNTRY_CODE,
                )
                statement.setString(4, provider.urlTemplate)
                statement.setInt(5, provider.priority)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    companion object {
        fun createDefault(): SqliteExternalSearchProviderRepository {
            val payload =
                requireNotNull(
                    SqliteExternalSearchProviderRepository::class.java.classLoader
                        .getResourceAsStream(com.permieware.osmapdigger.external.ExternalSearchProviderCatalog.RESOURCE_NAME),
                ) { "Missing packaged external search provider seed" }
                    .bufferedReader()
                    .use { it.readText() }
            return SqliteExternalSearchProviderRepository(
                DesktopSettingsDatabase.defaultPath(),
                com.permieware.osmapdigger.external.ExternalSearchProviderCatalog.decode(payload),
            )
        }
    }
}
