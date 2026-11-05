package com.babegetthis.android.testing

import com.babegetthis.android.core.reminder.ForegroundState
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.reminder.ReminderKicker
import com.babegetthis.android.core.reminder.WorkEnqueuer
import com.babegetthis.android.core.reminder.data.local.PendingReminder
import com.babegetthis.android.core.reminder.data.local.ReminderEligibility
import com.babegetthis.android.core.reminder.data.local.ReminderPrefs
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred

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

// Records each enqueue as (listId, generation, delayMillis) before it waits on
// gate, so a test holding gate open sees the enqueue as started but unfinished.
// onEnqueue runs first, letting a test inspect prefs at the moment of the call.
class FakeWorkEnqueuer : WorkEnqueuer {
    val enqueued = mutableListOf<Triple<String, Long, Long>>()
    var cancels = 0
        private set
    var gate: CompletableDeferred<Unit>? = null
    var onEnqueue: (() -> Unit)? = null

    override suspend fun enqueueUnique(listId: String, generation: Long, delayMillis: Long) {
        onEnqueue?.invoke()
        enqueued += Triple(listId, generation, delayMillis)
        gate?.await()
    }

    override suspend fun cancelUnique() {
        cancels++
    }
}

// ReminderEligibility is a final class over Room, so the fake hands out a
// MockK instance whose answer is read from partlyShopped on every call.
class FakeEligibility(var partlyShopped: (String) -> Boolean = { true }) {
    val eligibility: ReminderEligibility = mockk {
        coEvery { isPartlyShopped(any()) } answers { partlyShopped(firstArg()) }
    }
}

class FakeNotificationGate(var enabled: Boolean = true) : NotificationGate {
    override fun notificationsEnabled(): Boolean = enabled
}

class FakeForegroundState(var foreground: Boolean = false) : ForegroundState {
    override fun isForeground(): Boolean = foreground
}

class FakeClock(var now: Long = 0L) : ReminderClock {
    override fun now(): Long = now
}

class FakeReminderKicker : ReminderKicker {
    val pickUps = mutableListOf<String>()
    val edits = mutableListOf<String>()

    override fun onLocalPickUp(listId: String) {
        pickUps += listId
    }

    override fun onLocalEdit(listId: String) {
        edits += listId
    }
}
