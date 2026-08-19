package com.babegetthis.android.core.telemetry.data

import com.babegetthis.android.core.error.AppError

// Decides which handled errors are worth transmitting.
//
// This is the single most important object in core/telemetry, and it works by
// sending LESS. This app is offline-first: a user on a train generates network
// failures continuously, and at any real install base those alone would
// outnumber genuine defects by orders of magnitude. A Crashlytics dashboard
// that is 99% "no internet connection" is one nobody opens, which catches
// nothing at all. Filtering hard is what keeps it useful.
//
// It lives behind CrashReporter.recordNonFatal rather than beside it, so the
// filter is not a convention a call site can decline to follow.
object ErrorReportingPolicy {

    fun shouldReport(error: AppError): Boolean = when (error) {
        // safeCall met an exception it had no mapping for. That is close to a
        // definition of "we did not anticipate this", and it is the single
        // best signal in the set.
        is AppError.UnknownError -> true

        // Room failed. Never expected, never the user's fault, and the class
        // of bug most likely to be silently corrupting data.
        is AppError.DatabaseError -> true

        // Everything below is an ordinary outcome of running this app, not a
        // defect. Each is listed rather than folded into an `else` so that
        // adding an AppError subtype forces a decision here instead of
        // defaulting into silence.

        // Offline-first. This is Tuesday.
        is AppError.NetworkError -> false
        is AppError.TimeoutError -> false

        // The UI is meant to catch these first; when one slips through, the
        // user sees a message and fixes their input. Nothing to triage.
        is AppError.ValidationError -> false

        // Wrong password, expired session, a share code that matched no list.
        // All user-facing conditions with user-facing recovery.
        is AppError.AuthError -> false
        is AppError.UnauthorizedError -> false
        is AppError.NotFoundError -> false

        // 5xx is the backend's problem and is already visible in the
        // backend's own monitoring. Duplicating it here would mean one outage
        // arriving as thousands of client reports.
        is AppError.ServerError -> false
    }
}
