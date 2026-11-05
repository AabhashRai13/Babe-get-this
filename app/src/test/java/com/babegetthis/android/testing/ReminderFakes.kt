package com.babegetthis.android.testing

import com.babegetthis.android.core.reminder.data.local.PendingReminder
import com.babegetthis.android.core.reminder.data.local.ReminderPrefs

// In-memory ReminderPrefs. failCommits simulates a disk write that did not
// stick: both commit functions return false and change nothing. commitLog
// records each successful commit as "pending:<listId>:<gen>" or "clear".
class FakeReminderPrefs : ReminderPrefs {
    override var enabled = true
    override var generationCounter = 0L
        private set
    private var pending: PendingReminder? = null
    override var promptSnoozedUntil = 0L
    override var permissionRequested = false

    var failCommits = false
    val commitLog = mutableListOf<String>()

    override fun readPending(): PendingReminder? = pending

    override fun commitPending(record: PendingReminder): Boolean {
        if (failCommits) return false
        generationCounter = record.generation
        pending = record
        commitLog += "pending:${record.listId}:${record.generation}"
        return true
    }

    override fun commitClearPending(): Boolean {
        if (failCommits) return false
        pending = null
        commitLog += "clear"
        return true
    }
}
