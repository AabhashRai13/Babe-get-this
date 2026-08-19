package com.babegetthis.android.core.telemetry.data

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.babegetthis.android.BuildConfig
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Firebase Analytics behind AnalyticsRepository. One of only two files in the
// codebase allowed to import com.google.firebase.analytics.
//
// Thin on purpose — the mapping logic lives in AnalyticsEventMapper, which is
// plain Kotlin and therefore testable and reusable. What is left here is the
// SDK call and the Bundle conversion, both of which a replacement backend
// throws away.
//
// No queue, no batching, no retry: the SDK already writes events to a local
// store and uploads on its own schedule (roughly hourly in the foreground, or
// when the queue fills), respecting connectivity. Rebuilding that would be
// pure waste, and worse than the original.
@Singleton
class FirebaseAnalyticsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AnalyticsRepository {

    private val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    override fun track(event: AnalyticsEvent) {
        val mapped = AnalyticsEventMapper.map(event)
        analytics.logEvent(mapped.name, mapped.toBundle())

        // GA4's reporting delay runs up to 24 hours, so without a local echo
        // the feedback loop on "did I wire this event correctly?" is a day
        // long. DebugView shortens it too, but needs an adb property and shows
        // one device. This costs two lines and is stripped from release.
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "${mapped.name} ${mapped.text}${mapped.numbers.ifEmpty { "" }}")
        }
    }

    override fun setUser(userId: String?) {
        analytics.setUserId(userId)
    }

    // The SDK persists this across process restarts on its own, so there is no
    // storage to reimplement here — TelemetryConsent keeps a mirror only
    // because Analytics exposes no getter to render the switch from.
    override fun setCollectionEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
    }

    private fun MappedEvent.toBundle(): Bundle = Bundle().apply {
        text.forEach { (key, value) -> putString(key, value) }
        numbers.forEach { (key, value) -> putLong(key, value) }
    }

    private companion object {
        const val TAG = "Analytics"
    }
}
