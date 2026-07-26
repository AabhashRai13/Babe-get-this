package com.babegetthis.android.core.pin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The throttle is the real defense for a 4-digit PIN, so it gets a real test.
class PinThrottleTest {

    private fun failNTimes(n: Int, nowWall: Long, nowElapsed: Long): PinThrottle.Next {
        var next = PinThrottle.Next(0, 0L, 0L)
        repeat(n) {
            next = PinThrottle.onFailure(next.attempts, next.untilWall, next.untilElapsed, nowWall, nowElapsed)
        }
        return next
    }

    @Test
    fun noLockoutBeforeThreshold() {
        val n = failNTimes(4, nowWall = 1_000, nowElapsed = 1_000)
        assertEquals(4, n.attempts)
        assertEquals(0L, PinThrottle.remainingMs(n.untilWall, n.untilElapsed, 1_000, 1_000))
    }

    @Test
    fun lockoutEscalatesPerGroupOfFive() {
        // 5th failure -> 30s
        var n = failNTimes(5, 1_000, 1_000)
        assertEquals(30_000L, PinThrottle.remainingMs(n.untilWall, n.untilElapsed, 1_000, 1_000))
        // 10th -> 2m
        n = failNTimes(10, 1_000, 1_000)
        assertEquals(120_000L, PinThrottle.remainingMs(n.untilWall, n.untilElapsed, 1_000, 1_000))
        // 20th -> 1h, and stays capped at 1h beyond
        n = failNTimes(20, 1_000, 1_000)
        assertEquals(3_600_000L, PinThrottle.remainingMs(n.untilWall, n.untilElapsed, 1_000, 1_000))
        n = failNTimes(25, 1_000, 1_000)
        assertEquals(3_600_000L, PinThrottle.remainingMs(n.untilWall, n.untilElapsed, 1_000, 1_000))
    }

    @Test
    fun movingWallClockForwardDoesNotClearLockout() {
        val n = failNTimes(5, nowWall = 1_000, nowElapsed = 1_000) // locks 30s on both clocks
        // Jump wall clock a year ahead; elapsed barely moves.
        val remaining = PinThrottle.remainingMs(
            n.untilWall, n.untilElapsed,
            nowWall = 1_000 + 31_536_000_000L, nowElapsed = 2_000,
        )
        assertTrue("still locked via elapsed clock", remaining > 0)
    }

    @Test
    fun rebootDoesNotClearLockout() {
        val n = failNTimes(5, nowWall = 1_000, nowElapsed = 1_000_000) // locks 30s
        // Reboot: elapsedRealtime resets toward 0; wall barely moved.
        val remaining = PinThrottle.remainingMs(
            n.untilWall, n.untilElapsed,
            nowWall = 2_000, nowElapsed = 0,
        )
        assertTrue("still locked via wall clock", remaining > 0)
    }

    @Test
    fun bothClocksPastExpiryUnlocks() {
        val n = failNTimes(5, nowWall = 1_000, nowElapsed = 1_000)
        val remaining = PinThrottle.remainingMs(
            n.untilWall, n.untilElapsed,
            nowWall = 1_000 + 30_001, nowElapsed = 1_000 + 30_001,
        )
        assertEquals(0L, remaining)
    }
}
