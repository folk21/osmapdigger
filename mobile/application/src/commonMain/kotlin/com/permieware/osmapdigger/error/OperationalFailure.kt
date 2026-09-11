package com.permieware.osmapdigger.error

import kotlinx.coroutines.CancellationException

/** Stable categories for expected runtime failures that may cross application boundaries. */
enum class OperationalFailureKind {
    DATASET_NOT_FOUND,
    DATASET_INVALID,
    DATASET_STORAGE,
    FILE_ACCESS,
    DATABASE,
    SETTINGS,
    SETTINGS_INCOMPATIBLE,
    EXTERNAL_ACTION,
    MAP,
    UNKNOWN,
}

/**
 * Structured expected runtime failure.
 *
 * [technicalMessage] and [cause] are diagnostic data and must not be rendered directly to users.
 * Presentation maps [kind] to localized application-owned text.
 */
data class OperationalFailure(
    val kind: OperationalFailureKind,
    val technicalMessage: String? = null,
    val cause: Throwable? = null,
)

/** Exception carrier used where existing Kotlin interfaces are exception-based. */
class OperationalFailureException(
    val failure: OperationalFailure,
) : RuntimeException(failure.technicalMessage, failure.cause)

/** Preserve an already-classified failure or classify an unexpected platform exception at a boundary. */
fun Throwable.toOperationalFailure(
    defaultKind: OperationalFailureKind = OperationalFailureKind.UNKNOWN,
    technicalContext: String? = null,
): OperationalFailure =
    when (this) {
        is OperationalFailureException -> failure
        else ->
            OperationalFailure(
                kind = defaultKind,
                technicalMessage = technicalContext ?: message,
                cause = this,
            )
    }

/**
 * Execute one expected operational boundary while preserving coroutine cancellation and prior classification.
 * Programmer/domain invariant failures should stay outside this helper and continue to use require/check.
 */
inline fun <T> operationalBoundary(
    kind: OperationalFailureKind,
    technicalContext: String,
    block: () -> T,
): T =
    try {
        block()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: OperationalFailureException) {
        throw failure
    } catch (failure: Throwable) {
        throw OperationalFailureException(
            OperationalFailure(
                kind = kind,
                technicalMessage = technicalContext,
                cause = failure,
            ),
        )
    }

fun operationalFailure(
    kind: OperationalFailureKind,
    technicalMessage: String,
    cause: Throwable? = null,
): Nothing = throw OperationalFailureException(OperationalFailure(kind, technicalMessage, cause))
