package com.babegetthis.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.featureflags.data.FeatureFlagRepository
import com.babegetthis.android.core.reminder.ReminderScheduler
import com.babegetthis.android.core.sync.SyncTrigger
import com.babegetthis.android.core.telemetry.TelemetryContext
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// @HiltAndroidApp tells Hilt "this is the root of the app, start here."
// It's like calling GetIt.instance.init() or wrapping your Flutter app in ProviderScope.
// Hilt generates code at compile time to wire up all your dependencies.
@HiltAndroidApp
class BabeGetThisApp :
    Application(),
    Configuration.Provider {

    @Inject lateinit var supabaseClient: SupabaseClient

    @Inject lateinit var authStateManager: AuthStateManager

    @Inject lateinit var featureFlagRepository: FeatureFlagRepository

    @Inject lateinit var featureFlagCache: FeatureFlagCache

    @Inject lateinit var syncTrigger: SyncTrigger

    @Inject lateinit var telemetryContext: TelemetryContext

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var reminderScheduler: ReminderScheduler

    // A getter, not a stored value: Hilt injects the fields in super.onCreate(),
    // and WorkManager reads this when it first initialises. The getter together
    // with the lazy WorkManagerEnqueuer (which only resolves WorkManager on its
    // first enqueue or cancel, never while reminderScheduler is being injected)
    // guarantees workerFactory is injected before WorkManager first initialises,
    // whatever order Hilt injects these fields in.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // App-scoped so it lives for the whole process (survives Activity recreation),
    // unlike collecting in MainActivity.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Static crash context (which build) before anything else can crash.
        // Firebase itself is already up by now — it initialises from a
        // ContentProvider, ahead of this method.
        telemetryContext.onAppStart()
        // Shared-list sync: push queued edits + catch up on foreground and on
        // connectivity return. No-ops for users who never shared a list.
        syncTrigger.register(this)
        // Shopping reminder: a sign-out cancels the pending job and clears its
        // record, so one account's reminder never fires into the next session.
        reminderScheduler.observeSignOuts(authStateManager.signOuts)
        // Fetch once per process start — non-realtime by design, see
        // docs/technical-decisions/003-in-app-update-and-feature-flags.md.
        appScope.launch {
            // A failed fetch (null) leaves the cache unloaded: "unknown", not
            // "off". WorkManager often wakes a dead process for the reminder job
            // with no network, and that must not read as a kill switch.
            featureFlagRepository.fetchFlags()?.let { featureFlagCache.update(it) }
            // Shopping reminder: the flag may just have turned off, so reconcile
            // the pending job. The scheduler acts only on a flag known to be off.
            reminderScheduler.onFlagsLoaded()
        }
        // Supabase auto-refreshes the session in the background and rotates the
        // access token. Persist each rotated token so AuthInterceptor stops
        // sending the stale one cached at login (the "session expired" bug).
        appScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        authStateManager.refreshToken(status.session.accessToken)
                        // Telemetry identity rides the same signal as auth
                        // identity so the two cannot drift apart. The Supabase
                        // UUID and nothing else — never the email, which this
                        // session object also carries.
                        telemetryContext.onUserChanged(status.session.user?.id)
                    }
                    // Supabase has definitively lost the session (refresh token
                    // expired/revoked). Mirror it locally — otherwise TokenManager
                    // keeps serving a dead token and the user finds out mid-action
                    // via a 401 ("records audio, then session expired").
                    // expireSession, not logout: the account's lists stay on
                    // screen and editable until the user signs in again — data
                    // never disappears over a technicality. Only the explicit
                    // sign-out in ProfileViewModel removes them (see
                    // docs/technical-decisions/008). A no-op for signed-out
                    // users, and for the explicit sign-out that also lands here
                    // — SupabaseAuthRepository clears our own state BEFORE the
                    // Supabase session, so no user id is left to expire.
                    is SessionStatus.NotAuthenticated -> {
                        authStateManager.expireSession()
                        telemetryContext.onUserChanged(null)
                    }
                    // RefreshFailure (offline/flaky network) is deliberately NOT a
                    // logout: we're offline-first and Supabase keeps retrying.
                    else -> Unit // Initializing / RefreshFailure
                }
            }
        }
    }
}
