package com.permieware.osmapdigger.settings

import android.database.sqlite.SQLiteDatabase
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary
import com.permieware.osmapdigger.error.operationalFailure
import java.io.File

/** Android SQLite executor for the shared application settings schema. */
class AndroidSettingsDatabase(
    val file: File,
) {
    fun initialize() =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not initialize Android settings database") {
            file.parentFile?.mkdirs()
            openDatabase().use { database ->
                val version = database.version
                if (version == 0) {
                    createSchema(database)
                } else {
                    val migrations =
                        ApplicationSettingsSchema.migrationsFrom(version)
                            ?: operationalFailure(
                                OperationalFailureKind.SETTINGS_INCOMPATIBLE,
                                "Unsupported settings database schema version $version; expected <= ${ApplicationSettingsSchema.VERSION}",
                            )
                    migrations.forEach { migration -> applyMigration(database, migration) }
                }
            }
        }

    fun openDatabase(): SQLiteDatabase =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not open Android settings database") {
            SQLiteDatabase.openOrCreateDatabase(file, null)
        }

    private fun createSchema(database: SQLiteDatabase) {
        database.inTransaction {
            ApplicationSettingsSchema.createStatements.forEach { sql -> execSQL(sql) }
            version = ApplicationSettingsSchema.VERSION
        }
    }

    private fun applyMigration(
        database: SQLiteDatabase,
        migration: SettingsMigration,
    ) {
        database.inTransaction {
            migration.statements.forEach { sql -> execSQL(sql) }
            version = migration.toVersion
        }
    }

    private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }
}
