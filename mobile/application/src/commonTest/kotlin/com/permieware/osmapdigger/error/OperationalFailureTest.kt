package com.permieware.osmapdigger.error

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class OperationalFailureTest {
    @Test
    fun boundaryClassifiesUnexpectedOperationalException() {
        val exception =
            assertFailsWith<OperationalFailureException> {
                operationalBoundary(OperationalFailureKind.SETTINGS, "settings read failed") {
                    throw IllegalStateException("driver detail")
                }
            }

        assertEquals(OperationalFailureKind.SETTINGS, exception.failure.kind)
        assertEquals("settings read failed", exception.failure.technicalMessage)
        assertEquals("driver detail", exception.failure.cause?.message)
    }

    @Test
    fun boundaryPreservesExistingClassification() {
        val original =
            OperationalFailureException(
                OperationalFailure(OperationalFailureKind.DATASET_INVALID, "invalid metadata"),
            )

        val actual =
            assertFailsWith<OperationalFailureException> {
                operationalBoundary(OperationalFailureKind.DATASET_STORAGE, "outer boundary") {
                    throw original
                }
            }

        assertSame(original, actual)
    }

    @Test
    fun cancellationIsNeverConvertedToOperationalFailure() {
        val cancellation = CancellationException("cancelled")
        val actual =
            assertFailsWith<CancellationException> {
                operationalBoundary(OperationalFailureKind.DATABASE, "query") {
                    throw cancellation
                }
            }

        assertSame(cancellation, actual)
    }
}
