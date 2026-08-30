package com.permieware.osmapdigger.desktop.settings

import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary
import com.permieware.osmapdigger.error.operationalFailure
import com.permieware.osmapdigger.settings.ApplicationSettingsSchema
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager

/** Desktop SQLite executor for the shared application settings schema. */
class DesktopSettingsDatabase(
    val path: Path,
) {
    fun initialize() =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not initialize Desktop settings database") {
            Files.createDirectories(path.toAbsolutePath().parent)
            Class.forName("org.sqlite.JDBC")
            openConnection().use { connection ->
                val version = userVersion(connection)
                if (version == 0) {
                    createSchema(connection)
                } else {
                    val migrations =
                        ApplicationSettingsSchema.migrationsFrom(version)
                            ?: operationalFailure(
                                OperationalFailureKind.SETTINGS_INCOMPATIBLE,
                                "Unsupported settings database schema version $version; expected <= ${ApplicationSettingsSchema.VERSION}",
                            )
                    migrations.forEach { migration -> applyMigration(connection, migration) }
                }
            }
        }

    fun openConnection(): Connection =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not open Desktop settings database") {
            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
        }

    private fun createSchema(connection: Connection) {
        connection.inTransaction {
            createStatement().use { statement ->
                ApplicationSettingsSchema.createStatements.forEach { sql -> statement.executeUpdate(sql) }
                statement.execute("PRAGMA user_version = ${ApplicationSettingsSchema.VERSION}")
            }
        }
    }

    private fun applyMigration(
        connection: Connection,
        migration: com.permieware.osmapdigger.settings.SettingsMigration,
    ) {
        connection.inTransaction {
            createStatement().use { statement ->
                migration.statements.forEach { sql -> statement.executeUpdate(sql) }
                statement.execute("PRAGMA user_version = ${migration.toVersion}")
            }
        }
    }

    private fun userVersion(connection: Connection): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { result ->
                result.next()
                result.getInt(1)
            }
        }

    private inline fun Connection.inTransaction(block: Connection.() -> Unit) {
        val previousAutoCommit = autoCommit
        autoCommit = false
        try {
            block()
            commit()
        } catch (failure: Throwable) {
            rollback()
            throw failure
        } finally {
            autoCommit = previousAutoCommit
        }
    }

    companion object {
        fun defaultPath(): Path =
            Paths.get(
                System.getProperty("user.home"),
                ".osmapdigger",
                "settings",
                "preferences.sqlite",
            )
    }
}
