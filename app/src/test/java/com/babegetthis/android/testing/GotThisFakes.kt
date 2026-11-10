package com.babegetthis.android.testing

import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs

// In-memory GotThisPrefs with the same defaults and never-lower reset rule as
// the SharedPreferences implementation.
class FakeGotThisPrefs : GotThisPrefs {
    override var keyVersion = 0
    override var backfillDone = false
    override var guestHoldoutNumber = 50
    private val resets = mutableMapOf<String, Long>()
    private val clears = mutableMapOf<String, Long>()
    override var usualsHiddenUntil = 0L
    override var lastUsualsShownDay = 0L
    override var guestNotifiedWeek: String? = null
    override var lastSummaryWeek: String? = null
    override var hintsEnabled = true
    override var runningLowEnabled = true
    override var killed = false
    override var holdoutOn = true

    override fun lastAppliedReset(accountId: String): Long? = resets[accountId]

    override fun setLastAppliedReset(accountId: String, millis: Long) {
        val current = resets[accountId]
        if (current == null || millis > current) resets[accountId] = millis
    }

    override fun pendingClear(accountId: String): Long? = clears[accountId]

    override fun setPendingClear(accountId: String, millis: Long?) {
        if (millis == null) clears.remove(accountId) else clears[accountId] = millis
    }
}
