package com.babegetthis.android.core.pin.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

// NOTE: method names here are camelCase, not the backticked sentences used
// everywhere in the JVM suite. Instrumented tests are dexed, and spaces in a
// method name need DEX 040 — which needs minSdk 30. This app is minSdk 24, so a
// backticked name fails the build with "Space characters in SimpleName ... are
// not allowed prior to DEX version 040". Same reason MigrationTest and
// PinSurvivesLogoutTest were already written this way.
//
// Instrumented, not a JVM unit test: PinStore is backed by
// EncryptedSharedPreferences, whose MasterKey needs the AndroidKeyStore
// provider. Robolectric has no such provider ("KeyStoreException:
// AndroidKeyStore not found"), so this is one of the few things that genuinely
// requires a device. PinRepository's tests use an in-memory stand-in instead —
// see InMemoryPinStore.
@RunWith(AndroidJUnit4::class)
class PinStoreTest {

    private lateinit var store: PinStore

    @Before
    fun setUp() {
        store = PinStore(ApplicationProvider.getApplicationContext())
        store.clearAll()
    }

    @Test
    fun aFreshStoreHasNoPin() {
        assertNull(store.pinHash)
        assertNull(store.pinSalt)
        assertNull(store.recoveryHash)
        assertNull(store.recoverySalt)
    }

    @Test
    fun aFreshStoreHasZeroedCounters() {
        assertEquals(0, store.attempts)
        assertEquals(0L, store.lockoutUntilWall)
        assertEquals(0L, store.lockoutUntilElapsed)
    }

    @Test
    fun pinHashAndSaltRoundTrip() {
        store.pinHash = "hash"
        store.pinSalt = "salt"

        assertEquals("hash", store.pinHash)
        assertEquals("salt", store.pinSalt)
    }

    @Test
    fun recoveryHashAndSaltRoundTrip() {
        store.recoveryHash = "rhash"
        store.recoverySalt = "rsalt"

        assertEquals("rhash", store.recoveryHash)
        assertEquals("rsalt", store.recoverySalt)
    }

    @Test
    fun countersRoundTrip() {
        store.attempts = 3
        store.lockoutUntilWall = 111L
        store.lockoutUntilElapsed = 222L

        assertEquals(3, store.attempts)
        assertEquals(111L, store.lockoutUntilWall)
        assertEquals(222L, store.lockoutUntilElapsed)
    }

    @Test
    fun clearAllWipesEveryField() {
        store.pinHash = "hash"
        store.pinSalt = "salt"
        store.recoveryHash = "rhash"
        store.recoverySalt = "rsalt"
        store.attempts = 4
        store.lockoutUntilWall = 111L
        store.lockoutUntilElapsed = 222L

        store.clearAll()

        assertNull(store.pinHash)
        assertNull(store.pinSalt)
        assertNull(store.recoveryHash)
        assertNull(store.recoverySalt)
        assertEquals(0, store.attempts)
        assertEquals(0L, store.lockoutUntilWall)
        assertEquals(0L, store.lockoutUntilElapsed)
    }

    // The PIN lives in its own prefs file (bgt_pin_prefs) precisely so
    // TokenManager.clear() on logout cannot take it with it.
    @Test
    fun aSecondInstanceSeesWhatTheFirstWrote() {
        store.pinHash = "hash"

        val reopened = PinStore(ApplicationProvider.getApplicationContext())

        assertEquals("hash", reopened.pinHash)
    }
}
