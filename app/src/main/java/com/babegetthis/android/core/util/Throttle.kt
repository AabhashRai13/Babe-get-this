package com.babegetthis.android.core.util

// Lets an action run at most once per window. The caller asks before acting;
// this only answers whether enough time has passed since the last yes.
//
// `now` is injected so tests can move time by hand. Production passes a
// monotonic clock (SystemClock.elapsedRealtime) — wall time can jump
// backwards on an NTP correction and would reopen or extend the window.
//
// A refused attempt does not restart the window: the next yes comes
// `windowMillis` after the last YES, not after the last ask. Otherwise a
// steady stream of asks could push the action out forever.
class Throttle(private val windowMillis: Long, private val now: () -> Long) {

    private var lastAcquiredAt: Long? = null

    @Synchronized
    fun tryAcquire(): Boolean {
        val at = now()
        val last = lastAcquiredAt
        if (last != null && at - last < windowMillis) return false
        lastAcquiredAt = at
        return true
    }
}
