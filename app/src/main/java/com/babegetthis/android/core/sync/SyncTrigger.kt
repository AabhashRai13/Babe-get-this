package com.babegetthis.android.core.sync

import android.app.Activity
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.sync.data.repository.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Kicks a push + catch-up whenever connectivity returns or the app comes to
// the foreground. No WorkManager in v1 (design.md open question, resolved to
// "foreground + connectivity callback"): a queued edit waits at most until
// the next time the user opens the app with signal — acceptable for a
// shopping list. Kicks are cheap and idempotent, so double-firing (e.g.
// registration + first foreground) is harmless.
class SyncTrigger(
    private val engine: SyncEngine,
    private val authStateManager: AuthStateManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SyncKicker {

    // Push-only: repositories kick this after local edits. Catch-up isn't
    // needed there — the partner's device pulls, ours already has the data.
    override fun pushSoon() {
        scope.launch { engine.push() }
    }

    fun register(app: Application) {
        // Sign-in is a kick: discovery (catchUpAllShared) is what materialises
        // the account's shared lists after login — and logging in changes
        // neither foreground state nor connectivity, so without this the
        // lists only appeared after the next app background/foreground cycle.
        // StateFlow dedups equal values, so this fires once per transition.
        scope.launch {
            authStateManager.authState.collect { state ->
                if (state is AuthState.Authenticated) kick()
            }
        }

        // Fires immediately on registration when a network is already up —
        // that's the app-start kick for free.
        app.getSystemService(ConnectivityManager::class.java)
            ?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = kick()
            })

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) kick() // 0 → 1 = app foregrounded
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun kick() {
        scope.launch {
            // Results intentionally dropped: failures stay queued (pendingSync
            // flags / sync points untouched) and the next kick retries.
            engine.push()
            engine.catchUpAllShared()
        }
    }
}
