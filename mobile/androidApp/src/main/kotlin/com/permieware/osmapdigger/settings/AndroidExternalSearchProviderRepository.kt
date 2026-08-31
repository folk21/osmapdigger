package com.permieware.osmapdigger.settings

import android.content.ContentValues
import android.content.Context
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderCatalog
import com.permieware.osmapdigger.external.ExternalSearchProviderRepository
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android app-private SQLite adapter for configurable external search providers. */
class AndroidExternalSearchProviderRepository(
    databaseFile: File,
    private val seedProviders: List<ExternalSearchProvider>,
) : ExternalSearchProviderRepository {
    private val database = AndroidSettingsDatabase(databaseFile)

    constructor(context: Context) : this(
        File(context.filesDir, "settings/preferences.sqlite"),
        context.assets.open(ExternalSearchProviderCatalog.RESOURCE_NAME)
            .bufferedReader()
            .use { ExternalSearchProviderCatalog.decode(it.readText()) },
    )

    override suspend fun providersFor(countryCode: String?): List<ExternalSearchProvider> =
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openDatabase().use { sqlite ->
                seedDefaults(sqlite)
                sqlite.rawQuery(
                    """
                    SELECT provider_id, title, country_code, url_template, priority, query_terms_override
                    FROM external_search_provider
                    WHERE enabled = 1
                      AND (country_code = ? OR country_code = ?)
                    ORDER BY priority, title, provider_id
                    """.trimIndent(),
                    arrayOf(
                        ApplicationSettingsSchema.GLOBAL_COUNTRY_CODE,
                        countryCode?.uppercase() ?: "",
                    ),
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val storedCountry = cursor.getString(2)
                            add(
                                ExternalSearchProvider(
                                    id = cursor.getString(0),
                                    title = cursor.getString(1),
                                    countryCode = storedCountry.takeUnless {
                                        it == ApplicationSettingsSchema.GLOBAL_COUNTRY_CODE
                                    },
                                    urlTemplate = cursor.getString(3),
                                    priority = cursor.getInt(4),
                                    queryTermsOverride = if (cursor.isNull(5)) null else cursor.getString(5),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override suspend fun updateQueryTerms(updates: List<ExternalSearchProviderTermsUpdate>) =
        withContext(Dispatchers.IO) {
            if (updates.isEmpty()) return@withContext
            database.initialize()
            database.openDatabase().use { sqlite ->
                seedDefaults(sqlite)
                sqlite.beginTransaction()
                try {
                    updates.forEach { update ->
                        val values = ContentValues()
                        val queryTermsOverride = update.queryTermsOverride
                        if (queryTermsOverride == null) {
                            values.putNull("query_terms_override")
                        } else {
                            values.put("query_terms_override", queryTermsOverride.trim())
                        }
                        val updated =
                            sqlite.update(
                                "external_search_provider",
                                values,
                                "provider_id = ? AND country_code = ?",
                                arrayOf(
                                    update.providerId,
                                    update.countryCode?.uppercase() ?: ApplicationSettingsSchema.GLOBAL_COUNTRY_CODE,
                                ),
                            )
                        check(updated == 1) {
                            "External search provider '${update.providerId}' is not stored for the requested country scope"
                        }
                    }
                    sqlite.setTransactionSuccessful()
                } finally {
                    sqlite.endTransaction()
                }
            }
        }

    private fun seedDefaults(database: android.database.sqlite.SQLiteDatabase) {
        seedProviders.forEach { provider ->
            database.execSQL(
                """
                INSERT OR IGNORE INTO external_search_provider(
                    provider_id, title, country_code, url_template, enabled, priority, query_terms_override
                ) VALUES (?, ?, ?, ?, 1, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    provider.id,
                    provider.title,
                    provider.countryCode ?: ApplicationSettingsSchema.GLOBAL_COUNTRY_CODE,
                    provider.urlTemplate,
                    provider.priority,
                    provider.queryTermsOverride,
                ),
            )
        }
    }
}
