package com.babegetthis.android.feature.gotthis.data

import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.watermark
import com.babegetthis.android.feature.gotthis.domain.GotThisIds
import com.babegetthis.android.feature.gotthis.domain.GotThisRules

// The one place that decides whether Got This hints and suggestions may show.
// Readers use the prefs copy of the flags, never the cache directly.
class GotThisAccess(private val flags: FeatureFlagCache, private val prefs: GotThisPrefs) {

    // Copies the flags into prefs. After a failed fetch the cache is unloaded and
    // the stored copy stays as an earlier load left it.
    fun onFlagsLoaded() {
        if (!flags.isLoaded) return
        prefs.killed = flags.isKnownOff(FLAG_GOT_THIS)
        prefs.holdoutOn = !flags.isKnownOff(FLAG_HOLDOUT)
    }

    fun isHoldout(accountId: String?): Boolean {
        val number = if (accountId == null) prefs.guestHoldoutNumber else GotThisIds.holdoutNumber(accountId)
        return prefs.holdoutOn && number < GotThisRules.HOLDOUT_PERCENT
    }

    fun showsHints(accountId: String?): Boolean = !prefs.killed && prefs.hintsEnabled && !isHoldout(accountId)

    fun showsRunningLow(accountId: String?): Boolean = !prefs.killed && prefs.runningLowEnabled && !isHoldout(accountId)

    // Held out: log what would have shown, show nothing. Each follows its own
    // Settings switch, so the holdout is measured as the treated group is.

    /** True when hints would show but the person is held out. */
    fun shadowsHints(accountId: String?): Boolean = !prefs.killed && prefs.hintsEnabled && isHoldout(accountId)

    /** True when running-low would show but the person is held out. */
    fun shadowsRunningLow(accountId: String?): Boolean =
        !prefs.killed && prefs.runningLowEnabled && isHoldout(accountId)

    /** The later of the last applied reset and a pending clear; null with neither. */
    fun watermark(accountId: String): Long? = prefs.watermark(accountId)

    companion object {
        const val FLAG_GOT_THIS = "got_this"
        const val FLAG_HOLDOUT = "got_this_holdout"
    }
}
