package com.babegetthis.android

import android.app.Application
import com.babegetthis.android.core.auth.data.SessionObserver
import com.babegetthis.android.core.data.di.ApplicationScope
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.featureflags.data.FeatureFlagRepository
import com.babegetthis.android.core.sync.SyncTrigger
import com.babegetthis.android.core.telemetry.TelemetryContext
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

// @HiltAndroidApp tells Hilt "this is the root of the app, start here."
// It's like calling GetIt.instance.init() or wrapping your Flutter app in ProviderScope.
// Hilt generates code at compile time to wire up all your dependencies.
@HiltAndroidApp
class BabeGetThisApp : Application() {

    @Inject lateinit var supabaseClient: SupabaseClient

    @Inject lateinit var sessionObserver: SessionObserver

    @Inject lateinit var featureFlagRepository: FeatureFlagRepository

    @Inject lateinit var featureFlagCache: FeatureFlagCache

    @Inject lateinit var syncTrigger: SyncTrigger

    @Inject lateinit var telemetryContext: TelemetryContext

    // The one process-wide scope (CoroutinesModule), shared with everything
    // else that outlives a screen — not a second private one that happened to
    // live equally long.
    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Static crash context (which build) before anything else can crash.
        // Firebase itself is already up by now — it initialises from a
        // ContentProvider, ahead of this method.
        telemetryContext.onAppStart()
        // Shared-list sync: push queued edits + catch up on foreground and on
        // connectivity return. No-ops for users who never shared a list.
        syncTrigger.register(this)
        // Fetch once per process start — non-realtime by design, see
        // docs/technical-decisions/003-in-app-update-and-feature-flags.md.
        // A Retrofit suspend call, so the network wait happens on OkHttp's
        // own threads and the cache update is in-memory: Default is fine.
        applicationScope.launch {
            featureFlagCache.update(featureFlagRepository.fetchFlags())
        }
        // Token rotation and session loss, mirrored into local auth state and
        // telemetry identity. See SessionObserver for the transitions and for
        // why it runs on the IO dispatcher.
        sessionObserver.observe(supabaseClient.auth.sessionStatus)
    }
}
