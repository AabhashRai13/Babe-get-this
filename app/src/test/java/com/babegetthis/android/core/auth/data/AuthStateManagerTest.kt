package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.auth.model.AuthState
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
}
