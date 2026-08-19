package com.babegetthis.android.core.pin.data

// Pure throttle math, kept free of Android/storage so it can be unit-tested on
// the JVM. Escalating lockout after each group of 5 consecutive failures.
internal object PinThrottle {
    const val THRESHOLD = 5
    val delaysMs = longArrayOf(30_000L, 120_000L, 600_000L, 3_600_000L) // 30s, 2m, 10m, 1h

    data class Next(val attempts: Int, val untilWall: Long, val untilElapsed: Long)

    // ponytail: honoring max(wall, elapsed) means a user who legitimately waited
    // out a lockout and then reboots can be re-locked for up to one tier delay
    // (elapsedRealtime reset makes the elapsed expiry look fresh). Accepted —
    // the anti-tamper guarantee is worth the rare, low-harm false lock.
    fun remainingMs(untilWall: Long, untilElapsed: Long, nowWall: Long, nowElapsed: Long): Long =
        maxOf(untilWall - nowWall, untilElapsed - nowElapsed).coerceAtLeast(0L)

    fun onFailure(
        attempts: Int,
        oldWall: Long,
        oldElapsed: Long,
        nowWall: Long,
        nowElapsed: Long,
    ): Next {
        val n = attempts + 1
        if (n % THRESHOLD != 0) return Next(n, oldWall, oldElapsed)
        val tier = ((n / THRESHOLD) - 1).coerceIn(0, delaysMs.lastIndex)
        val d = delaysMs[tier]
        return Next(n, nowWall + d, nowElapsed + d)
    }
}
