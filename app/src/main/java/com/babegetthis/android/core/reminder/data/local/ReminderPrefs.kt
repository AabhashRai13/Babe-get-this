package com.babegetthis.android.core.reminder.data.local

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit

/** The one reminder currently armed: which list, which generation, and when it was armed. */
data class PendingReminder(val listId: String, val generation: Long, val armedAt: Long)

interface ReminderPrefs {
    var enabled: Boolean

    /** Highest generation ever committed; written only by [commitPending]. */
    val generationCounter: Long

    /** The armed reminder, or null when none is stored or the record is incomplete. */
    fun readPending(): PendingReminder?

    /** Stores [record] and sets the counter to its generation in one commit. */
    fun commitPending(record: PendingReminder): Boolean

    /** Removes the pending record in one commit; the counter is kept. */
    fun commitClearPending(): Boolean

    var promptSnoozedUntil: Long
    var permissionRequested: Boolean
}

// Plain SharedPreferences, like the sync points: this is a tiny durable
// record the scheduler writes before enqueuing work and the worker checks at
// fire time. The commit functions use commit() so a caller learns whether
// the record actually reached disk before it enqueues or cancels anything.
class SharedPrefsReminderPrefs(context: Context) : ReminderPrefs {

    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    override var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_ENABLED, value) }
        }

    override val generationCounter: Long
        get() = prefs.getLong(KEY_GENERATION_COUNTER, 0L)

    override fun readPending(): PendingReminder? {
        val listId = prefs.getString(KEY_PENDING_LIST_ID, null) ?: return null
        if (!prefs.contains(KEY_PENDING_GENERATION) || !prefs.contains(KEY_PENDING_ARMED_AT)) {
            return null
        }
        return PendingReminder(
            listId = listId,
            generation = prefs.getLong(KEY_PENDING_GENERATION, 0L),
            armedAt = prefs.getLong(KEY_PENDING_ARMED_AT, 0L),
        )
    }

    // The Boolean from commit() is load-bearing (the scheduler refuses to enqueue
    // when it is false); the KTX edit(commit = true) discards it.
    @SuppressLint("UseKtx")
    override fun commitPending(record: PendingReminder): Boolean = prefs.edit()
        .putLong(KEY_GENERATION_COUNTER, record.generation)
        .putString(KEY_PENDING_LIST_ID, record.listId)
        .putLong(KEY_PENDING_GENERATION, record.generation)
        .putLong(KEY_PENDING_ARMED_AT, record.armedAt)
        .commit()

    // The Boolean from commit() is load-bearing (the scheduler refuses to enqueue
    // when it is false); the KTX edit(commit = true) discards it.
    @SuppressLint("UseKtx")
    override fun commitClearPending(): Boolean = prefs.edit()
        .remove(KEY_PENDING_LIST_ID)
        .remove(KEY_PENDING_GENERATION)
        .remove(KEY_PENDING_ARMED_AT)
        .commit()

    override var promptSnoozedUntil: Long
        get() = prefs.getLong(KEY_PROMPT_SNOOZED_UNTIL, 0L)
        set(value) {
            prefs.edit { putLong(KEY_PROMPT_SNOOZED_UNTIL, value) }
        }

    override var permissionRequested: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_PERMISSION_REQUESTED, value) }
        }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_GENERATION_COUNTER = "generation_counter"
        const val KEY_PENDING_LIST_ID = "pending_list_id"
        const val KEY_PENDING_GENERATION = "pending_generation"
        const val KEY_PENDING_ARMED_AT = "pending_armed_at"
        const val KEY_PROMPT_SNOOZED_UNTIL = "prompt_snoozed_until"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
    }
}
