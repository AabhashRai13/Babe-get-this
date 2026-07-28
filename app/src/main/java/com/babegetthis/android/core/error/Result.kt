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

// Helper function to wrap database/network calls in try/catch.
// Any repository function can use this instead of writing try/catch everywhere.
// Like a reusable wrapper: final result = await safeCall(() => api.getItems());
//
// `onUnauthorized` lets the caller override the 401 → AppError mapping.
// Default is UnauthorizedError ("Session expired..."), which is right for
// authenticated endpoints where 401 truly means the token went stale. Login
// and register override it to AuthError("Invalid email or password.") because
// for those endpoints, 401 means wrong credentials — never "session expired".
//
// `onClientError` maps other 4xx codes (400/404/422/…). The default is an
// auth-flavored message, which only fits auth endpoints — non-auth callers like
// transcribe override it (a 400 there means "bad audio", not "auth failed").

suspend fun <T> safeCall(
    onUnauthorized: () -> AppError = { AppError.UnauthorizedError() },
    onClientError: (code: Int) -> AppError = { AppError.AuthError("Request failed.") },
    block: suspend () -> T,
): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        // MUST rethrow, and must come before the general catch below.
        // CancellationException is an Exception, so `catch (e: Exception)` used to
        // swallow it and hand back Result.Error(UnknownError) — meaning a cancelled
        // coroutine reported itself as a failed operation and its parent never
        // learned it was cancelled. That is a structured-concurrency break, and it
        // had a visible symptom: dismissing the voice sheet cancels the in-flight
        // transcribe job, the cancellation came back as an error Result, and the
        // sheet rendered a failure for something the user deliberately dismissed.
        throw e
    } catch (e: Exception) {
        // Map the exception to the right AppError type
        val error = when (e) {
            // An AppError the caller chose deliberately — pass it straight
            // through rather than re-deriving one from the exception type.
            is AppErrorException -> e.appError

            // Database errors
            is android.database.sqlite.SQLiteException -> AppError.DatabaseError()

            // Network errors — no internet, DNS failure, connection refused
            is java.net.UnknownHostException -> AppError.NetworkError()
            is java.net.ConnectException -> AppError.NetworkError("Cannot reach server.")
            is javax.net.ssl.SSLException -> AppError.NetworkError("Secure connection failed.")

            // Timeout
            is java.net.SocketTimeoutException -> AppError.TimeoutError()

            // HTTP errors from Retrofit — server returned an error status code
            is retrofit2.HttpException -> when (e.code()) {
                401 -> onUnauthorized()
                in 400..499 -> onClientError(e.code())
                in 500..599 -> AppError.ServerError(e.code(), "Server error. Please try later.")
                else -> AppError.ServerError(e.code(), e.message ?: "Unexpected server response.")
            }

            // Everything else
            else -> AppError.UnknownError(e.message ?: "An unexpected error occurred.")
        }
        Result.Error(error)
    }
}
