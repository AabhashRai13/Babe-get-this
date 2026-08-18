package com.babegetthis.android.core.telemetry

import com.babegetthis.android.core.telemetry.model.AnalyticsEvent

// The only analytics surface feature code is allowed to touch.
//
// Firebase is behind this on purpose. GA4 is a weak product-analytics tool
// (no real funnel exploration without BigQuery, up to 24h reporting delay)
// and we expect to outgrow it. Everything vendor-shaped — event names,
// parameter bundles, SDK singletons — lives in core/telemetry/data. Swapping
// backends is a new implementation class plus one @Binds line in
// core/telemetry/di/TelemetryModule.kt.
//
// Deliberately two methods. Anything wider (user properties, session control,
// consent) is added when something needs it, because every method here is
// surface a future implementation has to reproduce.
interface AnalyticsRepository {

    // Records that something happened. Events are types, not strings — see
    // AnalyticsEvent for why, and for the full catalog of what this app tracks.
    fun track(event: AnalyticsEvent)

    // The Supabase user UUID while signed in, null on sign-out. Nothing else
    // ever goes here: no email, no display name.
    fun setUser(userId: String?)

    // The user's opt-out, applied at the vendor. Lives on this interface rather
    // than in a wrapper because only the vendor client can actually stop
    // collecting — anything above it could suppress track() calls and still
    // leave the SDK gathering sessions and installation ids on its own.
    fun setCollectionEnabled(enabled: Boolean)
}
