package com.babegetthis.android

import android.app.Application
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.featureflags.data.FeatureFlagRepository
import com.babegetthis.android.core.sync.SyncTrigger
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
class BabeGetThisApp : Application() {

    @Inject lateinit var supabaseClient: SupabaseClient
    @Inject lateinit var authStateManager: AuthStateManager
    @Inject lateinit var featureFlagRepository: FeatureFlagRepository
    @Inject lateinit var featureFlagCache: FeatureFlagCache
    @Inject lateinit var syncTrigger: SyncTrigger

    // App-scoped so it lives for the whole process (survives Activity recreation),
    // unlike collecting in MainActivity.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Shared-list sync: push queued edits + catch up on foreground and on
        // connectivity return. No-ops for users who never shared a list.
        syncTrigger.register(this)
        // Fetch once per process start — non-realtime by design, see
        // docs/technical-decisions/003-in-app-update-and-feature-flags.md.
        appScope.launch {
            featureFlagCache.update(featureFlagRepository.fetchFlags())
        }
        // Supabase auto-refreshes the session in the background and rotates the
        // access token. Persist each rotated token so AuthInterceptor stops
        // sending the stale one cached at login (the "session expired" bug).
        appScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated ->
                        authStateManager.refreshToken(status.session.accessToken)
                    // Supabase has definitively lost the session (refresh token
                    // expired/revoked). Mirror it locally — otherwise TokenManager
                    // keeps serving a dead token and the user finds out mid-action
                    // via a 401 ("records audio, then session expired"). Harmless
                    // for logged-out users: logout() on empty prefs is a no-op.
                    // Deliberately does NOT evict shared-list replicas: data
                    // never leaves the device over a technicality. Only the
                    // explicit sign-out in ProfileViewModel evicts (see
                    // docs/technical-decisions/004).
                    is SessionStatus.NotAuthenticated ->
                        authStateManager.logout()
                    // RefreshFailure (offline/flaky network) is deliberately NOT a
                    // logout: we're offline-first and Supabase keeps retrying.
                    else -> Unit // Initializing / RefreshFailure
                }
            }
        }
    }
}
