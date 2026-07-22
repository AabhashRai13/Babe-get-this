package com.babegetthis.android.core.pin

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babegetthis.android.core.auth.data.TokenManager
import com.babegetthis.android.core.pin.data.PinStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// The PIN must survive logout and account switching. TokenManager.clear() wipes
// its own prefs file wholesale; this proves it does not touch the PIN's separate
// file. If someone ever merges the two files, this test fails loudly.
@RunWith(AndroidJUnit4::class)
class PinSurvivesLogoutTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val pinStore = PinStore(context)
    private val tokenManager = TokenManager(context)

    @Before
    fun setup() {
        pinStore.clearAll()
        tokenManager.clear()
    }

    @After
    fun cleanup() {
        pinStore.clearAll()
        tokenManager.clear()
    }

    @Test
    fun tokenManagerClear_leavesPinIntact() {
        pinStore.pinHash = "some-hash"
        pinStore.pinSalt = "some-salt"
        tokenManager.saveToken("token")

        tokenManager.clear() // logout

        assertEquals("some-hash", pinStore.pinHash)
        assertEquals("some-salt", pinStore.pinSalt)
    }
}
