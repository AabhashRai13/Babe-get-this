package com.babegetthis.android.core.auth.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Instrumented for the same reason as PinStoreTest: TokenManager is backed by
// EncryptedSharedPreferences, whose MasterKey needs the AndroidKeyStore provider,
// which Robolectric does not have. AuthStateManager's logic is tested on the JVM
// against an in-memory stand-in — see inMemoryTokenManager.
@RunWith(AndroidJUnit4::class)
class TokenManagerTest {

    private lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        tokenManager = TokenManager(ApplicationProvider.getApplicationContext())
        tokenManager.clear()
    }

    @Test
    fun aFreshStoreHoldsNothing() {
        assertNull(tokenManager.getToken())
        assertNull(tokenManager.getUserId())
        assertNull(tokenManager.getUserName())
        assertNull(tokenManager.getUserEmail())
    }

    @Test
    fun everyFieldRoundTrips() {
        tokenManager.saveToken("token")
        tokenManager.saveUserId("u1")
        tokenManager.saveUserName("Aabhash")
        tokenManager.saveUserEmail("a@b.c")

        assertEquals("token", tokenManager.getToken())
        assertEquals("u1", tokenManager.getUserId())
        assertEquals("Aabhash", tokenManager.getUserName())
        assertEquals("a@b.c", tokenManager.getUserEmail())
    }

    @Test
    fun savingAgainOverwrites() {
        tokenManager.saveToken("first")
        tokenManager.saveToken("second")

        assertEquals("second", tokenManager.getToken())
    }

    // The logout bug this app fixed once already: clear() must take the name and
    // email with it, not just the token, or the next account inherits them.
    @Test
    fun clearWipesEveryField() {
        tokenManager.saveToken("token")
        tokenManager.saveUserId("u1")
        tokenManager.saveUserName("Aabhash")
        tokenManager.saveUserEmail("a@b.c")

        tokenManager.clear()

        assertNull(tokenManager.getToken())
        assertNull(tokenManager.getUserId())
        assertNull(tokenManager.getUserName())
        assertNull(tokenManager.getUserEmail())
    }

    @Test
    fun aSecondInstanceSeesPersistedValues() {
        tokenManager.saveToken("token")

        val reopened = TokenManager(ApplicationProvider.getApplicationContext())

        assertEquals("token", reopened.getToken())
    }

    // TokenManager and PinStore use SEPARATE prefs files on purpose, so that
    // clearing the session on logout cannot take the device PIN with it.
    @Test
    fun clearingTheSessionLeavesThePinStoreAlone() {
        val pinStore = com.babegetthis.android.core.pin.data.PinStore(
            ApplicationProvider.getApplicationContext()
        )
        pinStore.pinHash = "pin-hash"
        tokenManager.saveToken("token")

        tokenManager.clear()

        assertEquals("pin-hash", pinStore.pinHash)
        pinStore.clearAll()
    }
}
