package com.babegetthis.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.data.SessionObserver
import com.babegetthis.android.core.data.di.ApplicationScope
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.featureflags.data.FeatureFlagRepository
import com.babegetthis.android.core.reminder.ReminderScheduler
import com.babegetthis.android.core.sync.SyncTrigger
import com.babegetthis.android.core.telemetry.TelemetryContext
import com.babegetthis.android.feature.gotthis.data.GotThisAccess
import com.babegetthis.android.feature.gotthis.data.GotThisBackfill
import com.babegetthis.android.feature.gotthis.reminder.RunningLowScheduler
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    @Inject lateinit var sessionObserver: SessionObserver

    @Inject lateinit var featureFlagRepository: FeatureFlagRepository

    @Inject lateinit var featureFlagCache: FeatureFlagCache

    @Inject lateinit var syncTrigger: SyncTrigger

    @Inject lateinit var telemetryContext: TelemetryContext

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject lateinit var gotThisAccess: GotThisAccess

    @Inject lateinit var gotThisBackfill: GotThisBackfill

    @Inject lateinit var runningLowScheduler: RunningLowScheduler

    // The one process-wide scope (CoroutinesModule), shared with everything
    // else that outlives a screen — not a second private one that happened to
    // live equally long.
    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    // A getter, not a stored value: Hilt injects the fields in super.onCreate(),
    // and WorkManager reads this when it first initialises. The getter together
    // with the lazy WorkManagerEnqueuer (which only resolves WorkManager on its
    // first enqueue or cancel, never while reminderScheduler is being injected)
    // guarantees workerFactory is injected before WorkManager first initialises,
    // whatever order Hilt injects these fields in.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

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
        // A Retrofit suspend call, so the network wait happens on OkHttp's
        // own threads and the cache update is in-memory: Default is fine.
        applicationScope.launch {
            // A failed fetch (null) leaves the cache unloaded: "unknown", not
            // "off". WorkManager often wakes a dead process for the reminder job
            // with no network, and that must not read as a kill switch.
            featureFlagRepository.fetchFlags()?.let { featureFlagCache.update(it) }
            // Shopping reminder: the flag may just have turned off, so reconcile
            // the pending job. The scheduler acts only on a flag known to be off.
            reminderScheduler.onFlagsLoaded()
            // Got This: copy the kill switch and holdout flags into its prefs.
            gotThisAccess.onFlagsLoaded()
        }
        // Got This: turn already-ticked guest items into history, once. Room
        // work, so off the main thread.
        applicationScope.launch(Dispatchers.IO) { gotThisBackfill.runOnce() }
        // Got This: the daily check behind the weekly running-low notification.
        // KEEP makes this a no-op once the periodic work exists.
        applicationScope.launch(Dispatchers.IO) { runningLowScheduler.ensureScheduled() }
        // Token rotation and session loss, mirrored into local auth state and
        // telemetry identity. See SessionObserver for the transitions and for
        // why it runs on the IO dispatcher.
        sessionObserver.observe(supabaseClient.auth.sessionStatus)
    }
}
