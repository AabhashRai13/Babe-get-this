package com.babegetthis.android.core.error

import javax.inject.Inject
import javax.inject.Singleton

// Wraps every database/network call a repository makes in one try/catch and
// maps whatever it throws to an AppError, so ViewModels only ever see a Result.
// Like a reusable wrapper: final result = await safeCall(() => api.getItems());
//
// A class rather than a top-level function so the reporter is an ordinary
// injected dependency. Repositories hold it as `safeCall` and call it through
// `invoke`, which keeps the call sites reading as they always did.
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
@Singleton
class SafeCall @Inject constructor(private val reporter: ErrorReporter) {

    suspend operator fun <T> invoke(
        onUnauthorized: () -> AppError = { AppError.UnauthorizedError() },
        onClientError: (code: Int) -> AppError = { AppError.AuthError("Request failed.") },
        block: suspend () -> T,
    ): Result<T> = try {
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
        val error = mapToAppError(e, onUnauthorized, onClientError)
        // Offer it to crash reporting. What actually gets transmitted is
        // decided by ErrorReportingPolicy, behind CrashReporter.recordNonFatal
        // — most of these are ordinary offline-first outcomes and are dropped.
        //
        // Positioned after the mapping and after the CancellationException
        // rethrow above, so a cancelled coroutine never reaches it: dismissing
        // the voice sheet is a user gesture, not a defect.
        //
        // runCatching: a failure inside reporting must never turn a handled
        // error into an unhandled one.
        runCatching { reporter.report(e, error) }
        Result.Error(error)
    }

    private fun mapToAppError(
        e: Exception,
        onUnauthorized: () -> AppError,
        onClientError: (code: Int) -> AppError,
    ): AppError = when (e) {
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

        // Supabase (Postgrest, Auth, RPC) throws its own types, so the
        // java.net and Retrofit arms above never see them. They used to land
        // in UnknownError, which is logged and sent to crash reporting, and a
        // RestException's message embeds the request headers: the
        // Authorization bearer token, and with it the user's email. Mapped by
        // status like Retrofit, and the message is never passed through.
        is io.github.jan.supabase.exceptions.RestException -> when (e.statusCode) {
            401 -> onUnauthorized()
            in 400..499 -> onClientError(e.statusCode)
            else -> AppError.ServerError(e.statusCode, "Server error. Please try later.")
        }
        // Supabase wraps every transport failure (DNS, refused, reset) in this.
        is io.github.jan.supabase.exceptions.HttpRequestException -> AppError.NetworkError()
        is io.ktor.client.plugins.HttpRequestTimeoutException -> AppError.TimeoutError()

        // Everything else. Deliberately does NOT pass e.message through:
        // this value is rendered straight into a snackbar, and an
        // unrecognised exception's text is internal detail. The concrete
        // leak was a missing cache file — FileNotFoundException's message is
        // the full path, so the voice flow would have shown the user
        // "/data/user/0/.../cache/voice-1717200000000.m4a (No such file or
        // directory)". SupabaseAuthRepository already took this care with
        // provider text; safeCall did the opposite for everyone else.
        //
        // The raw text was never actionable for a user, so it is logged for
        // diagnosis instead of surfaced.
        else -> {
            // runCatching: android.util.Log throws on plain-JVM unit tests
            // (not mocked); on device/Robolectric it logs normally.
            runCatching { android.util.Log.e("SafeCall", "Unrecognised exception → UnknownError", e) }
            AppError.UnknownError()
        }
    }
}
