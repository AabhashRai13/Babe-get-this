package com.babegetthis.android.feature.gotthis.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class SharedPrefsGotThisPrefsTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPrefsGotThisPrefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("got_this", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = SharedPrefsGotThisPrefs(context)
    }

    @Test
    fun `defaults`() {
        assertEquals(0, prefs.keyVersion)
        assertFalse(prefs.backfillDone)
        assertNull(prefs.lastAppliedReset("a"))
        assertNull(prefs.pendingClear("a"))
        assertEquals(0L, prefs.usualsHiddenUntil)
        assertEquals(0L, prefs.lastUsualsShownDay)
        assertNull(prefs.guestNotifiedWeek)
        assertNull(prefs.lastSummaryWeek)
        assertTrue(prefs.hintsEnabled)
        assertTrue(prefs.runningLowEnabled)
        assertTrue(prefs.killed)
        assertTrue(prefs.holdoutOn)
    }

    @Test
    fun `plain values round trip`() {
        prefs.keyVersion = 2
        prefs.backfillDone = true
        prefs.usualsHiddenUntil = 5L
        prefs.lastUsualsShownDay = 20_000L
        prefs.guestNotifiedWeek = "2026-W45"
        prefs.lastSummaryWeek = "2026-W44"
        prefs.hintsEnabled = false
        prefs.runningLowEnabled = false
        prefs.killed = true
        prefs.holdoutOn = false

        val other = SharedPrefsGotThisPrefs(context)
        assertEquals(2, other.keyVersion)
        assertTrue(other.backfillDone)
        assertEquals(5L, other.usualsHiddenUntil)
        assertEquals(20_000L, other.lastUsualsShownDay)
        assertEquals("2026-W45", other.guestNotifiedWeek)
        assertEquals("2026-W44", other.lastSummaryWeek)
        assertFalse(other.hintsEnabled)
        assertFalse(other.runningLowEnabled)
        assertTrue(other.killed)
        assertFalse(other.holdoutOn)
    }

    @Test
    fun `guest holdout number is drawn once and stable across instances`() {
        val first = SharedPrefsGotThisPrefs(context, Random(1)).guestHoldoutNumber
        val second = SharedPrefsGotThisPrefs(context, Random(2)).guestHoldoutNumber

        assertEquals(first, second)
        assertTrue(first in 0..99)
        assertEquals(first, prefs.guestHoldoutNumber)
    }

    @Test
    fun `guest holdout number comes from the random source`() {
        val expected = Random(7).nextInt(100)

        assertEquals(expected, SharedPrefsGotThisPrefs(context, Random(7)).guestHoldoutNumber)
    }

    @Test
    fun `setLastAppliedReset never lowers`() {
        prefs.setLastAppliedReset("a", 100L)
        prefs.setLastAppliedReset("a", 50L)
        assertEquals(100L, prefs.lastAppliedReset("a"))

        prefs.setLastAppliedReset("a", 100L)
        prefs.setLastAppliedReset("a", 200L)
        assertEquals(200L, prefs.lastAppliedReset("a"))
    }

    @Test
    fun `pending clear can be set and removed`() {
        prefs.setPendingClear("a", 10L)
        assertEquals(10L, prefs.pendingClear("a"))

        prefs.setPendingClear("a", 5L)
        assertEquals(5L, prefs.pendingClear("a"))

        prefs.setPendingClear("a", null)
        assertNull(prefs.pendingClear("a"))
    }

    @Test
    fun `per-account keys do not collide`() {
        prefs.setLastAppliedReset("a", 1L)
        prefs.setPendingClear("a", 2L)
        prefs.setLastAppliedReset("b", 3L)

        assertEquals(1L, prefs.lastAppliedReset("a"))
        assertEquals(2L, prefs.pendingClear("a"))
        assertEquals(3L, prefs.lastAppliedReset("b"))
        assertNull(prefs.pendingClear("b"))
    }
}
