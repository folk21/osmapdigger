package com.permieware.osmapdigger.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationSettingsSchemaTest {
    @Test
    fun legacyVersionOneHasOneDeterministicPathToCurrentSchema() {
        val migrations = requireNotNull(ApplicationSettingsSchema.migrationsFrom(1))

        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6), migrations.map { it.fromVersion to it.toVersion })
        assertEquals(ApplicationSettingsSchema.VERSION, migrations.last().toVersion)
        assertTrue(migrations.flatMap { it.statements }.any { "preferences_json" in it })
        assertTrue(migrations.flatMap { it.statements }.any { "candidate_scope_json" in it })
        assertTrue(migrations.flatMap { it.statements }.any { "favorite_settlement" in it })
        assertTrue(migrations.flatMap { it.statements }.any { "favorite_analysis_snapshot" in it })
        assertTrue(migrations.flatMap { it.statements }.any { "note_text" in it })
    }

    @Test
    fun historicalFavoriteMigrationDoesNotIncludeFutureNoteColumn() {
        val versionFourMigration = requireNotNull(ApplicationSettingsSchema.migrationsFrom(4)).first()
        val versionFiveMigration = requireNotNull(ApplicationSettingsSchema.migrationsFrom(5)).first()

        assertTrue(versionFourMigration.statements.any { "favorite_settlement" in it })
        assertTrue(versionFourMigration.statements.none { "note_text" in it })
        assertTrue(versionFiveMigration.statements.any { "ADD COLUMN note_text" in it })
    }

    @Test
    fun currentVersionRequiresNoMigration() {
        assertEquals(emptyList(), ApplicationSettingsSchema.migrationsFrom(ApplicationSettingsSchema.VERSION))
    }

    @Test
    fun futureOrUninitializedVersionsAreNotTreatedAsMigratableLegacyState() {
        assertNull(ApplicationSettingsSchema.migrationsFrom(0))
        assertNull(ApplicationSettingsSchema.migrationsFrom(ApplicationSettingsSchema.VERSION + 1))
    }
}
