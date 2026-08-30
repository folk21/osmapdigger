package com.permieware.osmapdigger.external

import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary

/** Classifies provider-registry storage failures without changing provider selection semantics. */
class OperationalExternalSearchProviderRepository(
    private val delegate: ExternalSearchProviderRepository,
) : ExternalSearchProviderRepository {
    override suspend fun providersFor(countryCode: String?): List<ExternalSearchProvider> =
        operationalBoundary(OperationalFailureKind.SETTINGS, "Could not load external search providers") {
            delegate.providersFor(countryCode)
        }
}
