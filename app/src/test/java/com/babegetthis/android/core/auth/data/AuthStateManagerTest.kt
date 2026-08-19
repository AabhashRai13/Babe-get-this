package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.testing.inMemoryTokenManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `initialize with a missing token becomes Unauthenticated`() {
        every { tokenManager.getToken() } returns null
        every { tokenManager.getUserId() } returns "u1"

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
