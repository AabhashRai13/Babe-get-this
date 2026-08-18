package com.babegetthis.android.core.telemetry.data

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.telemetry.CrashKey
import com.babegetthis.android.core.telemetry.CrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

// Crashlytics behind CrashReporter. One of only two files in the codebase
// allowed to import com.google.firebase.
//
// Uncaught crashes need no code here at all — Crashlytics installs its own
// Thread.UncaughtExceptionHandler during Firebase's ContentProvider init,
// before Application.onCreate runs. Everything below is about making those
// automatic reports worth reading: the trail that led there, the state at the
// time, and which user it happened to.
@Singleton
class CrashlyticsCrashReporter @Inject constructor() : CrashReporter {

    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    override fun breadcrumb(message: String) {
        crashlytics.log(message)
    }

    override fun setKey(key: CrashKey, value: String) {
        crashlytics.setCustomKey(key.key, value)
    }

    override fun recordNonFatal(throwable: Throwable, error: AppError) {
        // The filter, applied where no call site can skip it. See
        // ErrorReportingPolicy for why the list is this short.
        if (!ErrorReportingPolicy.shouldReport(error)) return

        // The AppError type is attached as a key so that reports group by our
        // classification rather than only by stack trace — two different bugs
        // can share a stack frame, and the same bug can arrive through
        // several.
        crashlytics.setCustomKey(KEY_APP_ERROR, error::class.simpleName ?: "Unknown")
        crashlytics.recordException(throwable)
    }

    override fun setUser(userId: String?) {
        // Crashlytics has no null overload; empty string is how it clears.
        crashlytics.setUserId(userId ?: "")
    }

    // Persisted by the SDK, and readable back via isCrashlyticsCollectionEnabled
    // — unlike Analytics, which offers no getter.
    override fun setCollectionEnabled(enabled: Boolean) {
        crashlytics.isCrashlyticsCollectionEnabled = enabled
    }

    private companion object {
        const val KEY_APP_ERROR = "app_error"
    }
}
