package com.babegetthis.android.feature.gotthis.data

import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.testing.FakeGotThisPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GotThisAccessTest {

    private val flags = FeatureFlagCache()
    private val prefs = FakeGotThisPrefs()
    private val access = GotThisAccess(flags, prefs)

    // holdout number 12
    private val regular = "22222222-2222-2222-2222-222222222222"

    // holdout number 2
    private val heldOut = "11111111-1111-1111-1111-111111111111"

    @Test
    fun `an account with number 12 is not in the holdout`() {
        assertFalse(access.isHoldout(regular))
        assertTrue(access.showsHints(regular))
        assertTrue(access.showsRunningLow(regular))
        assertFalse(access.shadowsHints(regular))
        assertFalse(access.shadowsRunningLow(regular))
    }

    @Test
    fun `an account with number 2 is in the holdout and only shadows`() {
        assertTrue(access.isHoldout(heldOut))
        assertFalse(access.showsHints(heldOut))
        assertFalse(access.showsRunningLow(heldOut))
        assertTrue(access.shadowsHints(heldOut))
        assertTrue(access.shadowsRunningLow(heldOut))
    }

    @Test
    fun `each switch stops its own shadowing in the holdout`() {
        prefs.hintsEnabled = false
        assertFalse(access.shadowsHints(heldOut))
        assertTrue(access.shadowsRunningLow(heldOut))

        prefs.hintsEnabled = true
        prefs.runningLowEnabled = false
        assertTrue(access.shadowsHints(heldOut))
        assertFalse(access.shadowsRunningLow(heldOut))
    }

    @Test
    fun `a guest uses the drawn number`() {
        prefs.guestHoldoutNumber = 9
        assertTrue(access.isHoldout(null))

        prefs.guestHoldoutNumber = 10
        assertFalse(access.isHoldout(null))
    }

    @Test
    fun `holdout off ends the holdout`() {
        prefs.holdoutOn = false

        assertFalse(access.isHoldout(heldOut))
        assertTrue(access.showsHints(heldOut))
        assertFalse(access.shadowsHints(heldOut))
        assertFalse(access.shadowsRunningLow(heldOut))
    }

    @Test
    fun `killed hides everything and stops shadowing`() {
        prefs.killed = true

        assertFalse(access.showsHints(regular))
        assertFalse(access.showsRunningLow(regular))
        assertFalse(access.shadowsHints(heldOut))
        assertFalse(access.shadowsRunningLow(heldOut))
    }

    @Test
    fun `each switch hides only its part`() {
        prefs.hintsEnabled = false
        assertFalse(access.showsHints(regular))
        assertTrue(access.showsRunningLow(regular))

        prefs.hintsEnabled = true
        prefs.runningLowEnabled = false
        assertTrue(access.showsHints(regular))
        assertFalse(access.showsRunningLow(regular))
    }

    @Test
    fun `watermark is the later of reset and pending clear`() {
        assertNull(access.watermark(regular))

        prefs.setLastAppliedReset(regular, 100L)
        assertEquals(100L, access.watermark(regular))

        prefs.setPendingClear(regular, 300L)
        assertEquals(300L, access.watermark(regular))

        prefs.setPendingClear(regular, 200L)
        assertEquals(200L, access.watermark(regular))
    }

    @Test
    fun `flags not loaded leave stored values unchanged`() {
        prefs.killed = true
        prefs.holdoutOn = false

        access.onFlagsLoaded()

        assertTrue(prefs.killed)
        assertFalse(prefs.holdoutOn)
    }

    @Test
    fun `loaded flags that are on clear the kill switch and end the holdout when holdout is off`() {
        flags.update(mapOf("got_this" to true, "got_this_holdout" to false))
        prefs.killed = true

        access.onFlagsLoaded()

        assertFalse(prefs.killed)
        assertFalse(prefs.holdoutOn)
    }

    @Test
    fun `loaded flags without a got_this row kill the feature`() {
        flags.update(mapOf("got_this_holdout" to true))

        access.onFlagsLoaded()

        assertTrue(prefs.killed)
        assertTrue(prefs.holdoutOn)
    }

    @Test
    fun `loaded flags that are off kill the feature and end the holdout`() {
        flags.update(mapOf("got_this" to false, "got_this_holdout" to false))

        access.onFlagsLoaded()

        assertTrue(prefs.killed)
        assertFalse(prefs.holdoutOn)
    }
}
