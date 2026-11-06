package com.babegetthis.android.core.error

// Result wraps every repository return value.
// Forces the ViewModel to handle both success and failure — no uncaught exceptions.

// Like Either<Failure, T> from dartz in Flutter,
// or AsyncValue from Riverpod.

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: AppError) : Result<Nothing>()
}

// Lets code inside a safeCall block raise a SPECIFIC AppError — validation
// failures, not-found — without safeCall needing to know about any feature's own
// exception types. Repositories used to throw bare IllegalStateException for
// these, which fell through to the `else` branch below and surfaced the raw
// exception text to the user as an UnknownError.
class AppErrorException(val appError: AppError) : Exception(appError.message)
