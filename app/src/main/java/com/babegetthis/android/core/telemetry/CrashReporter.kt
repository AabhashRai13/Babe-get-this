package com.babegetthis.android.core.telemetry

import com.babegetthis.android.core.error.AppError

// The only crash-reporting surface feature code is allowed to touch.
//
// Shaped so that a Sentry implementation is a drop-in: breadcrumbs, tags
// (setKey), captured exceptions (recordNonFatal), and user context all have
// direct Sentry equivalents. Kept separate from AnalyticsRepository because
// the two will almost certainly move to different vendors at different times,
// and a combined facade would need splitting exactly when that is expensive.
interface CrashReporter {

    // A step on the path to a failure — screen entered, sync started, item
    // saved. Shows up in the crash report as an ordered trail.
    //
    // RULE: actions and outcomes only. Never item names, transcription text,
    // email addresses, invite codes, or anything else the user authored. A
    // crash report is read by us, but it also leaves the device.
    fun breadcrumb(message: String)

    // State attached to every subsequent report. The key is an enum rather
    // than a string so the set stays small and stable — Crashlytics caps
    // custom keys at 64, and a typo'd key is a silently-lost one.
    fun setKey(key: CrashKey, value: String)

    // Reports a handled error. NOT everything handed here is transmitted:
    // the implementation applies ErrorReportingPolicy and drops the expected
    // failures. That filtering lives behind this method precisely so no call
    // site can opt out of it.
    fun recordNonFatal(throwable: Throwable, error: AppError)

    // The Supabase user UUID while signed in, null on sign-out — enough to
    // tell "one user crashing ten times" from "ten users crashing once",
    // which are very different bugs. Nothing identifying beyond the UUID.
    fun setUser(userId: String?)

    // The user's opt-out. Separate from the analytics switch on purpose: crash
    // reporting has a stronger legitimate-interest argument than product
    // analytics does, and the two may well diverge (see the GDPR open question
    // in the change's design doc). Keeping them independent means that change
    // is a policy edit, not a UI rewrite.
    fun setCollectionEnabled(enabled: Boolean)
}

// The reproduction state we attach to every report. Bounded on purpose:
// each of these answers a question a triaging engineer would otherwise have
// to ask the user, and nothing here is free-form.
enum class CrashKey(val key: String) {
    // Which build — a crash only in `dev` usually means local backend config.
    Flavor("flavor"),

    // Signed in or not. Half the app works signed out, so this splits the
    // offline-first paths from the synced ones.
    AuthState("auth_state"),

    // Connectivity at crash time. Offline-first means many code paths only
    // execute in one of these two states.
    NetworkState("network_state"),

    // Pending sync operations. A deep queue points at sync-engine bugs.
    SyncQueueDepth("sync_queue_depth"),

    // Bucketed, never exact — see Buckets. Large lists surface pagination
    // and rendering bugs small ones never reach.
    ItemCountBucket("item_count_bucket"),

    // Last screen entered, from the Screen enum. Raw nav routes are never
    // used: SHOPPING_ITEMS carries a user-authored list name.
    LastScreen("last_screen"),
}
