package com.lucaskivi.cream.core

/**
 * Result of a network operation that can succeed with a value or fail with a cause.
 *
 * Intentionally minimal — extend with domain-specific failure variants if call sites
 * need to differentiate between failure modes.
 */
sealed interface NetworkResult<out T> {
    /**
     * Successful outcome carrying the produced [value].
     */
    data class Success<T>(val value: T) : NetworkResult<T>

    /**
     * Failed outcome carrying the underlying [cause].
     */
    data class Failure(val cause: Throwable) : NetworkResult<Nothing>
}

/**
 * Invokes [action] with the success value when this is a [NetworkResult.Success].
 *
 * @param action Receiver invoked with the success value; not called on failure.
 * @return This [NetworkResult] unchanged, for chaining.
 */
inline fun <T> NetworkResult<T>.onSuccess(action: (T) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Success) action(value)
    return this
}

/**
 * Invokes [action] with the failure cause when this is a [NetworkResult.Failure].
 *
 * @param action Receiver invoked with the failure cause; not called on success.
 * @return This [NetworkResult] unchanged, for chaining.
 */
inline fun <T> NetworkResult<T>.onFailure(action: (Throwable) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Failure) action(cause)
    return this
}

/**
 * Transforms the success value via [transform], leaving a failure unchanged.
 *
 * @param transform Mapping applied only when this is a [NetworkResult.Success].
 * @return A [NetworkResult] of the transformed type, preserving the original failure if any.
 */
inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> =
    when (this) {
        is NetworkResult.Success -> NetworkResult.Success(transform(value))
        is NetworkResult.Failure -> this
    }
