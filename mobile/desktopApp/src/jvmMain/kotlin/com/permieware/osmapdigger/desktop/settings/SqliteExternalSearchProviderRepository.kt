package com.permieware.osmapdigger.desktop.settings

import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderCatalog
import com.permieware.osmapdigger.external.ExternalSearchProviderRepository
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.settings.ApplicationSettingsSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Connection
import java.sql.Types

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
                    SELECT provider_id, title, country_code, url_template, priority, query_terms_override
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
                                        queryTermsOverride = result.getString("query_terms_override"),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

    override suspend fun updateQueryTerms(updates: List<ExternalSearchProviderTermsUpdate>) =
        withContext(Dispatchers.IO) {
            if (updates.isEmpty()) return@withContext
            database.initialize()
            database.openConnection().use { connection ->
                seedDefaults(connection)
                connection.autoCommit = false
                try {
                    connection.prepareStatement(
                        """
                        UPDATE external_search_provider
                        SET query_terms_override = ?
                        WHERE provider_id = ? AND country_code = ?
                        """.trimIndent(),
                    ).use { statement ->
                        updates.forEach { update ->
                            val queryTermsOverride = update.queryTermsOverride
                            if (queryTermsOverride == null) {
                                statement.setNull(1, Types.VARCHAR)
                            } else {
                                statement.setString(1, queryTermsOverride.trim())
                            }
                            statement.setString(2, update.providerId)
                            statement.setString(
                                3,
                                update.countryCode?.uppercase() ?: ApplicationSettingsSchema.GLOBAL_COUNTRY_CODE,
                            )
                            check(statement.executeUpdate() == 1) {
                                "External search provider '${update.providerId}' is not stored for the requested country scope"
                            }
                        }
                    }
                    connection.commit()
                } catch (failure: Throwable) {
                    connection.rollback()
                    throw failure
                } finally {
                    connection.autoCommit = true
                }
            }
        }

    private fun seedDefaults(connection: Connection) {
        connection.prepareStatement(
            """
            INSERT OR IGNORE INTO external_search_provider(
                provider_id, title, country_code, url_template, enabled, priority, query_terms_override
            ) VALUES (?, ?, ?, ?, 1, ?, ?)
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
                if (provider.queryTermsOverride == null) {
                    statement.setNull(6, Types.VARCHAR)
                } else {
                    statement.setString(6, provider.queryTermsOverride)
                }
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
                        .getResourceAsStream(ExternalSearchProviderCatalog.RESOURCE_NAME),
                ) { "Missing packaged external search provider seed" }
                    .bufferedReader()
                    .use { it.readText() }
            return SqliteExternalSearchProviderRepository(
                DesktopSettingsDatabase.defaultPath(),
                ExternalSearchProviderCatalog.decode(payload),
            )
        }
    }
}
