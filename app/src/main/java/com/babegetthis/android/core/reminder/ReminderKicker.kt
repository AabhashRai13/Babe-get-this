package com.babegetthis.android.core.reminder

// Fire-and-forget seam, like SyncKicker: repositories call it after a local
// write so the reminder schedule follows the list without the UI write waiting
// on prefs or WorkManager, and without repositories knowing the scheduler.
interface ReminderKicker {
    /** An item on [listId] was picked up on this device; may arm or retarget the reminder. */
    fun onLocalPickUp(listId: String)

    /** [listId] was edited on this device; restarts the delay only if it is the pending list. */
    fun onLocalEdit(listId: String)
}
