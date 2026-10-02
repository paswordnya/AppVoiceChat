package com.pip.shared.core.result

/**
 * Every `commonMain` public API boundary returns this instead of throwing,
 * so a Kotlin exception never crosses into Swift/Java as a native-level
 * crash — PRD-KMP-Migration-v2.md §16 (Reliability NFR).
 */
sealed class PipResult<out T> {
    data class Success<T>(val value: T) : PipResult<T>()

    data class Failure(val error: PipError) : PipResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): PipResult<R> =
        when (this) {
            is Success -> Success(transform(value))
            is Failure -> this
        }

    inline fun onSuccess(action: (T) -> Unit): PipResult<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (PipError) -> Unit): PipResult<T> {
        if (this is Failure) action(error)
        return this
    }
}

/** Mirrors the shape of iOS's `APIError` (PRD §4.4's real REST/WS surface). */
sealed class PipError {
    data object InvalidUrl : PipError()

    data object Unauthorized : PipError()

    data object NoConnectivity : PipError()

    data class Server(val statusCode: Int, val message: String) : PipError()

    data class Decoding(val message: String) : PipError()

    data class Underlying(val message: String) : PipError()
}
