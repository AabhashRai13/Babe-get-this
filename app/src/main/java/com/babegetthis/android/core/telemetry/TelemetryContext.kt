package com.babegetthis.android.core.telemetry

import com.babegetthis.android.BuildConfig
import com.babegetthis.android.core.data.di.ApplicationScope
import com.babegetthis.android.core.error.ErrorReportingHook
import com.babegetthis.android.core.network.NetworkMonitor
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.Screen
import com.babegetthis.android.core.telemetry.model.bucketCount
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Keeps the ambient state attached to reports current: who the user is, where
// they are, and what shape their data is in.
//
// The point of this class is that a crash report should answer the questions a
// triaging engineer would otherwise have to ask the user — which build, signed
// in or not, online or not, how much data, how far behind was sync — none of
// which are recoverable after the fact.
//
// It reads DAOs directly. That is a layering shortcut: the repositories above
// them expose Flows shaped for the UI, and subscribing to those from here would
// mean holding subscriptions for the process lifetime to compute two numbers.
// A COUNT on screen change is both cheaper and easier to reason about.
@Singleton
class TelemetryContext @Inject constructor(
    private val analytics: AnalyticsRepository,
    private val crashReporter: CrashReporter,
    private val networkMonitor: NetworkMonitor,
    private val consent: TelemetryConsent,
    private val itemDao: ShoppingItemDao,
    private val listDao: ShoppingListDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    // Called once from Application.onCreate. Only the genuinely static key
    // belongs here — everything else would be stale by the time it mattered.
    fun onAppStart() {
        // Before anything is recorded: a user who opted out last run must not
        // have this launch collected while we get around to asking.
        consent.applyPersisted()
        crashReporter.setKey(CrashKey.Flavor, BuildConfig.FLAVOR)
        crashReporter.setKey(CrashKey.AuthState, SIGNED_OUT)
        // Connects safeCall to crash reporting. One assignment, at startup,
        // covering every repository in the app — see ErrorReportingHook for why
        // it is a property rather than an injected dependency.
        //
        // recordNonFatal applies ErrorReportingPolicy internally, so the
        // filtering cannot be skipped by anything that reaches this lambda.
        ErrorReportingHook.report = { throwable, error ->
            crashReporter.recordNonFatal(throwable, error)
        }
    }

    // Driven by the session collector in BabeGetThisApp, so analytics identity
    // and crash identity can never disagree — they are set from one place, on
    // one signal.
    fun onUserChanged(userId: String?) {
        analytics.setUser(userId)
        crashReporter.setUser(userId)
        crashReporter.setKey(CrashKey.AuthState, if (userId == null) SIGNED_OUT else SIGNED_IN)
        crashReporter.breadcrumb(if (userId == null) "auth: signed out" else "auth: signed in")
    }

    // The single navigation hook. Firebase auto-collects screen_view for
    // Activities only and this app has one Activity, so without this call
    // there is no screen data at all — and a crash report would not say where
    // the user was.
    //
    // Takes a Screen rather than a route on purpose: Routes.SHOPPING_ITEMS
    // carries a user-authored list name, and this is the boundary that must
    // not be handed one.
    fun onScreen(screen: Screen) {
        analytics.track(AnalyticsEvent.ScreenViewed(screen))
        crashReporter.breadcrumb("screen: ${screen.screenName}")
        crashReporter.setKey(CrashKey.LastScreen, screen.screenName)
        crashReporter.setKey(
            CrashKey.NetworkState,
            if (networkMonitor.isOnline()) ONLINE else OFFLINE,
        )
        refreshDataShape()
    }

    // applicationScope rather than a screen scope: this is fire-and-forget
    // context for a crash that may happen seconds later, and it must not be
    // cancelled by the navigation that triggered it.
    //
    // Failure here is deliberately silent. Telemetry that can break the app it
    // is watching is worse than telemetry that occasionally misses a number,
    // and this runs on every screen change.
    private fun refreshDataShape() {
        applicationScope.launch {
            runCatching {
                crashReporter.setKey(
                    CrashKey.ItemCountBucket,
                    bucketCount(itemDao.countActiveItems()),
                )
                // Lists and items are pushed by the same engine, so the depth
                // the user experiences as "not synced yet" is the total.
                val pending = itemDao.countPendingSync() + listDao.countPendingSync()
                crashReporter.setKey(CrashKey.SyncQueueDepth, bucketCount(pending))
            }
        }
    }

    private companion object {
        const val SIGNED_IN = "signed_in"
        const val SIGNED_OUT = "signed_out"
        const val ONLINE = "online"
        const val OFFLINE = "offline"
    }
}
