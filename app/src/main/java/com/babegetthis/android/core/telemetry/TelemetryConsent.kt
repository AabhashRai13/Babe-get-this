package com.babegetthis.android.core.telemetry

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// The user's say over what leaves their device.
//
// Both Firebase SDKs persist their own collection flag across process restarts,
// so this class deliberately does NOT reimplement storage — it calls the setters
// and lets the SDKs remember. The SharedPreferences here exist for one reason:
// Analytics offers no getter, so without a mirror the switch could not render
// its own state. Crashlytics does expose one, and is mirrored the same way only
// so both switches read from one place.
//
// That means the prefs are DISPLAY state, not the source of truth. If they ever
// disagreed with the SDKs, the SDKs would win — which is why applyPersisted()
// pushes the mirror back down at startup rather than reading it up.
//
// Analytics and crash reporting are two independent switches. Crash reporting
// has a stronger legitimate-interest argument than product analytics does, so
// the day EU consent forces analytics to opt-in, that is a default change here
// and nothing else.
@Singleton
class TelemetryConsent @Inject constructor(
    @ApplicationContext context: Context,
    private val analytics: AnalyticsRepository,
    private val crashReporter: CrashReporter,
) {

    private val prefs = context.getSharedPreferences("telemetry_consent", Context.MODE_PRIVATE)

    // Opt-OUT, so both default on. See the design doc's open question: this is
    // common practice but is not consent under GDPR for non-essential
    // analytics, and an EU launch may need `false` here for the analytics half.
    val analyticsEnabled: Boolean get() = prefs.getBoolean(KEY_ANALYTICS, true)
    val crashReportingEnabled: Boolean get() = prefs.getBoolean(KEY_CRASH, true)

    fun setAnalyticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYTICS, enabled).apply()
        analytics.setCollectionEnabled(enabled)
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CRASH, enabled).apply()
        crashReporter.setCollectionEnabled(enabled)
    }

    // Re-asserts the stored choice at startup.
    //
    // Strictly redundant while the SDKs persist their own flags — but "the
    // vendor remembers my users' opt-out for me" is exactly the assumption that
    // should not be load-bearing on a privacy control. It survives a vendor
    // swap to one that does not persist, and it costs two calls per launch.
    fun applyPersisted() {
        analytics.setCollectionEnabled(analyticsEnabled)
        crashReporter.setCollectionEnabled(crashReportingEnabled)
    }

    private companion object {
        const val KEY_ANALYTICS = "analytics_enabled"
        const val KEY_CRASH = "crash_reporting_enabled"
    }
}
