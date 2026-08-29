package com.permieware.osmapdigger.settings

import android.content.Context
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderRepository
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
        context.assets.open(com.permieware.osmapdigger.external.ExternalSearchProviderCatalog.RESOURCE_NAME)
            .bufferedReader()
            .use { com.permieware.osmapdigger.external.ExternalSearchProviderCatalog.decode(it.readText()) },
    )

    override suspend fun providersFor(countryCode: String?): List<ExternalSearchProvider> =
        withContext(Dispatchers.IO) {
            database.initialize()
            database.openDatabase().use { sqlite ->
                seedDefaults(sqlite)
                sqlite.rawQuery(
                    """
                    SELECT provider_id, title, country_code, url_template, priority
                    FROM external_search_provider
                    WHERE enabled = 1
                      AND (country_code = ? OR country_code = ?)
                    ORDER BY priority, title, provider_id
                    """.trimIndent(),
                    arrayOf(
                        AndroidSettingsDatabase.GLOBAL_COUNTRY_CODE,
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
                                        it == AndroidSettingsDatabase.GLOBAL_COUNTRY_CODE
                                    },
                                    urlTemplate = cursor.getString(3),
                                    priority = cursor.getInt(4),
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun seedDefaults(database: android.database.sqlite.SQLiteDatabase) {
        seedProviders.forEach { provider ->
            database.execSQL(
                """
                INSERT OR IGNORE INTO external_search_provider(
                    provider_id, title, country_code, url_template, enabled, priority
                ) VALUES (?, ?, ?, ?, 1, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    provider.id,
                    provider.title,
                    provider.countryCode ?: AndroidSettingsDatabase.GLOBAL_COUNTRY_CODE,
                    provider.urlTemplate,
                    provider.priority,
                ),
            )
        }
    }
}
