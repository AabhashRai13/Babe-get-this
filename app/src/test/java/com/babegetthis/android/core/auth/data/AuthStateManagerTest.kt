package com.babegetthis.android.core.auth.data

import app.cash.turbine.test
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.testing.inMemoryTokenManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthStateManagerTest {

    private lateinit var tokenManager: TokenManager
    private lateinit var authStateManager: AuthStateManager

    @Before
    fun setUp() {
        tokenManager = mockk(relaxed = true)
        authStateManager = AuthStateManager(tokenManager)
    }

    @Test
    fun `starts in Loading before initialize`() {
        assertEquals(AuthState.Loading, authStateManager.authState.value)
    }

    @Test
    fun `initialize with a saved token and userId becomes Authenticated`() {
        every { tokenManager.getToken() } returns "token-123"
        every { tokenManager.getUserId() } returns "u1"

        authStateManager.initialize()

        assertEquals(AuthState.Authenticated("u1"), authStateManager.authState.value)
    }

    @Test
    fun `initialize with a user id but no token becomes SessionExpired`() {
        every { tokenManager.getToken() } returns null
        every { tokenManager.getUserId() } returns "u1"

        authStateManager.initialize()

        assertEquals(AuthState.SessionExpired("u1"), authStateManager.authState.value)
    }

    @Test
    fun `initialize with neither token nor user id becomes Unauthenticated`() {
        every { tokenManager.getToken() } returns null
        every { tokenManager.getUserId() } returns null

        authStateManager.initialize()

        assertEquals(AuthState.Unauthenticated, authStateManager.authState.value)
    }

    @Test
    fun `initialize with a missing userId becomes Unauthenticated`() {
        every { tokenManager.getToken() } returns "token-123"
        every { tokenManager.getUserId() } returns null

        authStateManager.initialize()

        assertEquals(AuthState.Unauthenticated, authStateManager.authState.value)
    }

    @Test
    fun `login persists all fields and becomes Authenticated`() {
        authStateManager.login(
            token = "token-123",
            userId = "u1",
            userName = "Ann",
            userEmail = "a@b.com",
        )

        verify { tokenManager.saveToken("token-123") }
        verify { tokenManager.saveUserId("u1") }
        verify { tokenManager.saveUserName("Ann") }
        verify { tokenManager.saveUserEmail("a@b.com") }
        assertEquals(AuthState.Authenticated("u1"), authStateManager.authState.value)
    }

    @Test
    fun `logout clears storage and becomes Unauthenticated`() {
        // Put it in an authenticated state first.
        authStateManager.login("token-123", "u1", "Ann", "a@b.com")

        authStateManager.logout()

        verify { tokenManager.clear() }
        assertEquals(AuthState.Unauthenticated, authStateManager.authState.value)
    }

    // -- The helpers added so the repo no longer reaches around this class --

    @Test
    fun `updateName writes only the cached name`() {
        authStateManager.updateName("New Name")

        verify { tokenManager.saveUserName("New Name") }
    }

    @Test
    fun `currentEmail returns the cached email`() {
        every { tokenManager.getUserEmail() } returns "a@b.com"

        assertEquals("a@b.com", authStateManager.currentEmail())
    }

    @Test
    fun `currentEmail returns null when nothing is cached`() {
        every { tokenManager.getUserEmail() } returns null

        assertNull(authStateManager.currentEmail())
    }

    // -- refreshToken --

    // Supabase rotates the access token in the background. Only the token is
    // persisted: authState and the cached name/email must not churn, or the UI
    // would flicker on every silent refresh.
    @Test
    fun `refreshToken persists only the token`() {
        val manager = AuthStateManager(inMemoryTokenManager())
        manager.login("old-token", "u1", "Aabhash", "a@b.c")

        manager.refreshToken("new-token")

        assertEquals(AuthState.Authenticated("u1"), manager.authState.value)
        assertEquals("Aabhash", manager.userName.value)
        assertEquals("a@b.c", manager.userEmail.value)
    }

    @Test
    fun `a refreshed token is what later reads see`() {
        val tokens = inMemoryTokenManager()
        val manager = AuthStateManager(tokens)
        manager.login("old-token", "u1", "Aabhash", "a@b.c")

        manager.refreshToken("new-token")

        assertEquals("new-token", tokens.getToken())
    }

    // -- round-trips against a real backing store --

    @Test
    fun `initialize restores what login persisted`() {
        val tokens = inMemoryTokenManager()
        AuthStateManager(tokens).login("t", "u1", "Aabhash", "a@b.c")

        val reopened = AuthStateManager(tokens)
        reopened.initialize()

        assertEquals(AuthState.Authenticated("u1"), reopened.authState.value)
        assertEquals("Aabhash", reopened.userName.value)
        assertEquals("a@b.c", reopened.userEmail.value)
    }

    // The logout bug this app already fixed once: a lingering session must not
    // survive into the next launch.
    @Test
    fun `initialize after logout stays unauthenticated`() {
        val tokens = inMemoryTokenManager()
        val manager = AuthStateManager(tokens)
        manager.login("t", "u1", "Aabhash", "a@b.c")
        manager.logout()

        val reopened = AuthStateManager(tokens)
        reopened.initialize()

        assertEquals(AuthState.Unauthenticated, reopened.authState.value)
        assertNull(reopened.userName.value)
        assertNull(reopened.userEmail.value)
    }

    @Test
    fun `switching accounts replaces the cached identity`() {
        val manager = AuthStateManager(inMemoryTokenManager())
        manager.login("t1", "u1", "First", "first@b.c")

        manager.login("t2", "u2", "Second", "second@b.c")

        assertEquals(AuthState.Authenticated("u2"), manager.authState.value)
        assertEquals("Second", manager.userName.value)
        assertEquals("second@b.c", manager.userEmail.value)
    }

    // -- session expiry (docs/technical-decisions/008, spec D6) --
    //
    // A lost session must not look like a sign-out: the account's lists stay
    // on screen, so the user id has to survive while the token goes.

    @Test
    fun `expireSession keeps the user id and clears only the token`() {
        val tokens = inMemoryTokenManager()
        val manager = AuthStateManager(tokens)
        manager.login("t", "u1", "Aabhash", "a@b.c")

        manager.expireSession()

        assertEquals(AuthState.SessionExpired("u1"), manager.authState.value)
        assertNull(tokens.getToken())
        assertEquals("u1", tokens.getUserId())
        assertEquals("Aabhash", manager.userName.value)
    }

    @Test
    fun `an expired session is still expired after a restart`() {
        val tokens = inMemoryTokenManager()
        AuthStateManager(tokens).apply {
            login("t", "u1", "Aabhash", "a@b.c")
            expireSession()
        }

        val reopened = AuthStateManager(tokens)
        reopened.initialize()

        assertEquals(AuthState.SessionExpired("u1"), reopened.authState.value)
    }

    @Test
    fun `expireSession is a no-op when no user id is stored`() {
        val manager = AuthStateManager(inMemoryTokenManager())
        manager.initialize()

        manager.expireSession()

        assertEquals(AuthState.Unauthenticated, manager.authState.value)
    }

    // Supabase can report the lost session from Application.onCreate, before
    // MainActivity has called initialize(). The state must stay Loading and
    // let initialize() derive SessionExpired from storage.
    @Test
    fun `expireSession before initialize leaves Loading and initialize finds the expired session`() {
        val tokens = inMemoryTokenManager()
        AuthStateManager(tokens).login("t", "u1", "Aabhash", "a@b.c")
        val manager = AuthStateManager(tokens)

        manager.expireSession()
        assertEquals(AuthState.Loading, manager.authState.value)

        manager.initialize()
        assertEquals(AuthState.SessionExpired("u1"), manager.authState.value)
    }

    // An explicit sign-out clears Supabase's session too, which fires the same
    // "session lost" event. Whichever arrives first, sign-out must win.
    @Test
    fun `explicit logout wins when the session event arrives first`() {
        val manager = AuthStateManager(inMemoryTokenManager())
        manager.login("t", "u1", "Aabhash", "a@b.c")

        manager.expireSession()
        manager.logout()

        assertEquals(AuthState.Unauthenticated, manager.authState.value)
    }

    @Test
    fun `explicit logout wins when the session event arrives last`() {
        val manager = AuthStateManager(inMemoryTokenManager())
        manager.login("t", "u1", "Aabhash", "a@b.c")

        manager.logout()
        manager.expireSession()

        assertEquals(AuthState.Unauthenticated, manager.authState.value)
    }

    // -- data owner: whose lists the device shows --

    @Test
    fun `dataOwnerId is the user id while signed in or expired and null otherwise`() {
        val manager = AuthStateManager(inMemoryTokenManager())
        assertNull(manager.dataOwnerId()) // Loading

        manager.initialize()
        assertNull(manager.dataOwnerId()) // Unauthenticated

        manager.login("t", "u1", "Aabhash", "a@b.c")
        assertEquals("u1", manager.dataOwnerId())

        manager.expireSession()
        assertEquals("u1", manager.dataOwnerId())
    }

    @Test
    fun `dataOwner skips Loading and emits only when the owner changes`() = runTest {
        val manager = AuthStateManager(inMemoryTokenManager())
        manager.dataOwner.test {
            expectNoEvents() // still Loading

            manager.initialize()
            assertEquals(null, awaitItem())

            manager.login("t", "u1", "Aabhash", "a@b.c")
            assertEquals("u1", awaitItem())

            manager.expireSession() // same owner — the lists on screen don't change
            expectNoEvents()

            manager.logout()
            assertEquals(null, awaitItem())
        }
    }

    @Test
    fun `updateName is visible to a later read`() {
        val tokens = inMemoryTokenManager()
        val manager = AuthStateManager(tokens)
        manager.login("t", "u1", "Aabhash", "a@b.c")

        manager.updateName("Renamed")

        assertEquals("Renamed", manager.userName.value)
        assertEquals("Renamed", tokens.getUserName())
    }
}
