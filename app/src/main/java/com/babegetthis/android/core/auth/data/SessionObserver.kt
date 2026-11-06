package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.data.di.ApplicationScope
import com.babegetthis.android.core.data.di.IoDispatcher
import com.babegetthis.android.core.telemetry.TelemetryContext
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Mirrors the Supabase session into the app's own state.
//
// Supabase auto-refreshes the session in the background and rotates the
// access token. Each rotated token is persisted so AuthInterceptor stops
// sending the stale one cached at login (the "session expired" bug), and
// telemetry identity rides the same signal as auth identity so the two cannot
// drift apart — they are set from one place, on one event.
//
// Lives in its own class rather than in BabeGetThisApp.onCreate so the
// transition logic is a unit test away, and so the dispatcher it runs on is an
// explicit dependency instead of whatever the Application happened to launch
// on. TokenManager writes EncryptedSharedPreferences — a disk write, plus
// keystore work on first open — which is IO, not Default.
@Singleton
class SessionObserver @Inject constructor(
    private val authStateManager: AuthStateManager,
    private val telemetryContext: TelemetryContext,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    // Called once from Application.onCreate. The collection lives as long as
    // the process (applicationScope), not an Activity.
    fun observe(sessionStatus: Flow<SessionStatus>) {
        applicationScope.launch(ioDispatcher) {
            sessionStatus.collect { onStatus(it) }
        }
    }

    private fun onStatus(status: SessionStatus) {
        when (status) {
            is SessionStatus.Authenticated -> {
                authStateManager.refreshToken(status.session.accessToken)
                // The Supabase UUID and nothing else — never the email, which
                // this session object also carries.
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
            // Initializing: nothing is known yet.
            is SessionStatus.RefreshFailure, SessionStatus.Initializing -> Unit
        }
    }
}
