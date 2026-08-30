package com.permieware.osmapdigger.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationSettingsSchemaTest {
    @Test
    fun legacyVersionOneHasOneDeterministicPathToCurrentSchema() {
        val migrations = requireNotNull(ApplicationSettingsSchema.migrationsFrom(1))

        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4), migrations.map { it.fromVersion to it.toVersion })
        assertEquals(ApplicationSettingsSchema.VERSION, migrations.last().toVersion)
        assertTrue(migrations.flatMap { it.statements }.any { "preferences_json" in it })
        assertTrue(migrations.flatMap { it.statements }.any { "candidate_scope_json" in it })
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
