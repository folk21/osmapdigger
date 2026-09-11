package com.permieware.osmapdigger.preferences

import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary

/** Classifies settings storage failures while keeping absence (`load() == null`) distinct from failure. */
class OperationalUserPreferencesRepository(
    private val delegate: UserPreferencesRepository,
) : UserPreferencesRepository {
    override suspend fun load(): UserPreferences? =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not load user preferences") {
            delegate.load()
        }

    override suspend fun save(preferences: UserPreferences) {
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not save user preferences") {
            delegate.save(preferences)
        }
    }
}
