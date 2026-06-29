package com.babegetthis.android

import android.app.Application
import com.babegetthis.android.core.auth.data.AuthStateManager
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

    // App-scoped so it lives for the whole process (survives Activity recreation),
    // unlike collecting in MainActivity.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Supabase auto-refreshes the session in the background and rotates the
        // access token. Persist each rotated token so AuthInterceptor stops
        // sending the stale one cached at login (the "session expired" bug).
        appScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated ->
                        authStateManager.refreshToken(status.session.accessToken)
                    else -> Unit // Initializing / NotAuthenticated / RefreshFailure
                }
            }
        }
    }
}
