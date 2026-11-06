package com.babegetthis.android.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleTest {

    private var now = 1_000L
    private val throttle = Throttle(windowMillis = 30_000L) { now }

    @Test
    fun `the first attempt is allowed`() {
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun `a second attempt inside the window is refused`() {
        throttle.tryAcquire()
        now += 29_999L

        assertFalse(throttle.tryAcquire())
    }

    @Test
    fun `an attempt once the window has elapsed is allowed`() {
        throttle.tryAcquire()
        now += 30_000L

        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun `a refused attempt does not restart the window`() {
        // Otherwise a user tapping between screens every few seconds would
        // push the next refresh out forever.
        throttle.tryAcquire()
        now += 20_000L
        throttle.tryAcquire()
        now += 10_000L

        assertTrue(throttle.tryAcquire())
    }
}
