package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.telemetry.TelemetryContext
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.lang.Exception

// The Supabase session is the source of truth for "who is signed in". This
// observer mirrors it into the app's own state (TokenManager via
// AuthStateManager) and into telemetry identity, from one place and on one
// signal, so the two can never disagree.
@OptIn(ExperimentalCoroutinesApi::class)
class SessionObserverTest {

    private val authStateManager = mockk<AuthStateManager>(relaxed = true)
    private val telemetryContext = mockk<TelemetryContext>(relaxed = true)
    private val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)

    private fun observer(ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher()) =
        SessionObserver(
            authStateManager = authStateManager,
            telemetryContext = telemetryContext,
            applicationScope = CoroutineScope(UnconfinedTestDispatcher()),
            ioDispatcher = ioDispatcher,
        )

    private fun authenticated(userId: String?, token: String = "access-1") = SessionStatus.Authenticated(
        session = UserSession(
            accessToken = token,
            refreshToken = "refresh-1",
            expiresIn = 3600,
            tokenType = "bearer",
            user = userId?.let { UserInfo(aud = "authenticated", id = it) },
        ),
        source = SessionSource.Refresh(mockk()),
    )

    @Test
    fun `a rotated access token is persisted`() = runTest {
        observer().observe(status)

        status.value = authenticated(userId = "user-1", token = "access-2")

        verify { authStateManager.refreshToken("access-2") }
    }

    @Test
    fun `telemetry identity follows the session user id`() = runTest {
        observer().observe(status)

        status.value = authenticated(userId = "user-1")

        verify { telemetryContext.onUserChanged("user-1") }
    }

    @Test
    fun `a session without a user still persists the token`() = runTest {
        observer().observe(status)

        status.value = authenticated(userId = null)

        verify { authStateManager.refreshToken("access-1") }
        verify { telemetryContext.onUserChanged(null) }
    }

    @Test
    fun `a lost session logs out locally and clears telemetry identity`() = runTest {
        observer().observe(status)

        status.value = SessionStatus.NotAuthenticated(isSignOut = false)

        verify { authStateManager.logout() }
        verify { telemetryContext.onUserChanged(null) }
    }

    @Test
    fun `a refresh failure is not a logout`() = runTest {
        // Offline-first: Supabase keeps retrying, and the user keeps their
        // lists in the meantime.
        observer().observe(status)

        status.value = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("offline")))

        verify(exactly = 0) { authStateManager.logout() }
        verify(exactly = 0) { telemetryContext.onUserChanged(any()) }
    }

    @Test
    fun `initializing touches nothing`() = runTest {
        observer().observe(status)

        verify(exactly = 0) { authStateManager.refreshToken(any()) }
        verify(exactly = 0) { authStateManager.logout() }
        verify(exactly = 0) { telemetryContext.onUserChanged(any()) }
    }

    @Test
    fun `session changes are handled on the IO dispatcher`() = runTest {
        // TokenManager writes EncryptedSharedPreferences — disk plus keystore
        // on first open — so this must not run on the scope's own dispatcher.
        // A StandardTestDispatcher shows the dispatch: nothing happens until
        // the IO dispatcher is run.
        val io = StandardTestDispatcher(testScheduler)
        observer(ioDispatcher = io).observe(status)
        status.value = authenticated(userId = "user-1")

        verify(exactly = 0) { authStateManager.refreshToken(any()) }
        io.scheduler.advanceUntilIdle()
        verify { authStateManager.refreshToken("access-1") }
    }
}
