package com.babegetthis.android.feature.gotthis.data.local

import android.content.Context
import androidx.core.content.edit
import kotlin.random.Random

interface GotThisPrefs {
    /** Version of the item-key algorithm the stored purchases were written with; 0 when never set. */
    var keyVersion: Int
    var backfillDone: Boolean

    /** Holdout number for people without an account, drawn once on first read (0..99). */
    val guestHoldoutNumber: Int

    fun lastAppliedReset(accountId: String): Long?

    /** Stores [millis] unless a later reset is already stored. */
    fun setLastAppliedReset(accountId: String, millis: Long)

    fun pendingClear(accountId: String): Long?

    /** Stores [millis], or removes the pending clear when null. */
    fun setPendingClear(accountId: String, millis: Long?)
    var usualsHiddenUntil: Long

    /** Epoch day the usuals were last shown. */
    var lastUsualsShownDay: Long
    var guestNotifiedWeek: String?
    var lastSummaryWeek: String?
    var hintsEnabled: Boolean
    var runningLowEnabled: Boolean

    /**
     * Copy of the `got_this` kill switch, written when flags load. True until
     * they first do, so a fresh install stays hidden and silent until the flag
     * says otherwise.
     */
    var killed: Boolean

    /** Copy of the `got_this_holdout` flag, written when flags load. */
    var holdoutOn: Boolean
}

/** The later of the last applied reset and a pending clear; null with neither. */
fun GotThisPrefs.watermark(accountId: String): Long? =
    listOfNotNull(lastAppliedReset(accountId), pendingClear(accountId)).maxOrNull()

// Plain SharedPreferences, like the reminder record. None of these values guards
// a race, so writes use apply().
class SharedPrefsGotThisPrefs(context: Context, private val random: Random = Random.Default) : GotThisPrefs {

    private val prefs = context.getSharedPreferences("got_this", Context.MODE_PRIVATE)

    override var keyVersion: Int
        get() = prefs.getInt(KEY_KEY_VERSION, 0)
        set(value) {
            prefs.edit { putInt(KEY_KEY_VERSION, value) }
        }

    override var backfillDone: Boolean
        get() = prefs.getBoolean(KEY_BACKFILL_DONE, false)
        set(value) {
            prefs.edit { putBoolean(KEY_BACKFILL_DONE, value) }
        }

    override val guestHoldoutNumber: Int
        get() {
            if (prefs.contains(KEY_GUEST_HOLDOUT)) return prefs.getInt(KEY_GUEST_HOLDOUT, 0)
            val drawn = random.nextInt(100)
            prefs.edit { putInt(KEY_GUEST_HOLDOUT, drawn) }
            return drawn
        }

    override fun lastAppliedReset(accountId: String): Long? = readLong(KEY_RESET + accountId)

    override fun setLastAppliedReset(accountId: String, millis: Long) {
        val current = lastAppliedReset(accountId)
        if (current == null || millis > current) {
            prefs.edit { putLong(KEY_RESET + accountId, millis) }
        }
    }

    override fun pendingClear(accountId: String): Long? = readLong(KEY_CLEAR + accountId)

    override fun setPendingClear(accountId: String, millis: Long?) {
        prefs.edit {
            if (millis == null) remove(KEY_CLEAR + accountId) else putLong(KEY_CLEAR + accountId, millis)
        }
    }

    override var usualsHiddenUntil: Long
        get() = prefs.getLong(KEY_USUALS_HIDDEN_UNTIL, 0L)
        set(value) {
            prefs.edit { putLong(KEY_USUALS_HIDDEN_UNTIL, value) }
        }

    override var lastUsualsShownDay: Long
        get() = prefs.getLong(KEY_LAST_USUALS_SHOWN_DAY, 0L)
        set(value) {
            prefs.edit { putLong(KEY_LAST_USUALS_SHOWN_DAY, value) }
        }

    override var guestNotifiedWeek: String?
        get() = prefs.getString(KEY_GUEST_NOTIFIED_WEEK, null)
        set(value) {
            prefs.edit { putString(KEY_GUEST_NOTIFIED_WEEK, value) }
        }

    override var lastSummaryWeek: String?
        get() = prefs.getString(KEY_LAST_SUMMARY_WEEK, null)
        set(value) {
            prefs.edit { putString(KEY_LAST_SUMMARY_WEEK, value) }
        }

    override var hintsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HINTS_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_HINTS_ENABLED, value) }
        }

    override var runningLowEnabled: Boolean
        get() = prefs.getBoolean(KEY_RUNNING_LOW_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_RUNNING_LOW_ENABLED, value) }
        }

    override var killed: Boolean
        get() = prefs.getBoolean(KEY_KILLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_KILLED, value) }
        }

    override var holdoutOn: Boolean
        get() = prefs.getBoolean(KEY_HOLDOUT_ON, true)
        set(value) {
            prefs.edit { putBoolean(KEY_HOLDOUT_ON, value) }
        }

    private fun readLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null

    private companion object {
        const val KEY_KEY_VERSION = "key_version"
        const val KEY_BACKFILL_DONE = "backfill_done"
        const val KEY_GUEST_HOLDOUT = "guest_holdout_number"
        const val KEY_RESET = "reset:"
        const val KEY_CLEAR = "clear:"
        const val KEY_USUALS_HIDDEN_UNTIL = "usuals_hidden_until"
        const val KEY_LAST_USUALS_SHOWN_DAY = "last_usuals_shown_day"
        const val KEY_GUEST_NOTIFIED_WEEK = "guest_notified_week"
        const val KEY_LAST_SUMMARY_WEEK = "last_summary_week"
        const val KEY_HINTS_ENABLED = "hints_enabled"
        const val KEY_RUNNING_LOW_ENABLED = "running_low_enabled"
        const val KEY_KILLED = "killed"
        const val KEY_HOLDOUT_ON = "holdout_on"
    }
}
