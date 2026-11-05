package com.babegetthis.android.core.reminder

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.reminder.data.local.PendingReminder
import com.babegetthis.android.core.reminder.data.local.ReminderEligibility
import com.babegetthis.android.core.reminder.data.local.ReminderPrefs
import com.babegetthis.android.core.telemetry.CrashReporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the one pending reminder: the durable record in [prefs] and the unique
 * job in [enqueuer], tied together by a generation number.
 *
 * Every mutation of the record and every enqueue or cancel happens inside the
 * single [mutex], and the enqueue/cancel is awaited before the lock is
 * released, so the last job WorkManager holds always carries the generation the
 * record holds. The record is committed before the job is enqueued: a job can
 * only ever fire against a record at least as new as itself, and a job whose
 * generation no longer matches does nothing.
 *
 * Locked functions never call each other; each one that needs a cancel calls
 * the private, unlocked [cancelAndClearLocked], because [Mutex] is not
 * re-entrant.
 */
class ReminderScheduler(
    private val enqueuer: WorkEnqueuer,
    private val prefs: ReminderPrefs,
    private val eligibility: ReminderEligibility,
    private val flags: FeatureFlagCache,
    private val gate: NotificationGate,
    private val crashReporter: CrashReporter,
    private val clock: ReminderClock,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReminderKicker {

    private val mutex = Mutex()

    override fun onLocalPickUp(listId: String) {
        scope.launch { arm(listId) }
    }

    override fun onLocalEdit(listId: String) {
        scope.launch { reset(listId) }
    }

    /**
     * Arms (or retargets) the reminder for [listId]. A closed gate cancels and
     * clears whatever is pending; a list that is not partly shopped leaves any
     * other list's reminder alone.
     */
    suspend fun arm(listId: String): Boolean = mutex.withLock {
        if (!gateOpen()) {
            cancelAndClearLocked()
            return@withLock false
        }
        if (!eligibility.isPartlyShopped(listId)) return@withLock false
        enqueueLocked(listId)
    }

    /** Restarts the delay with a new generation, only when [listId] is the pending list. */
    suspend fun reset(listId: String) {
        mutex.withLock {
            if (prefs.readPending()?.listId != listId) return@withLock
            if (!gateOpen()) {
                cancelAndClearLocked()
                return@withLock
            }
            enqueueLocked(listId)
        }
    }

    /** The worker's cheap pre-check. Unlocked; [finish] repeats it under the lock. */
    suspend fun owns(listId: String, generation: Long): Boolean {
        val record = prefs.readPending() ?: return false
        return record.listId == listId && record.generation == generation
    }

    /**
     * Under the lock: if the job still owns the record, runs [post] and then
     * clears the record, even when [post] throws or the caller is cancelled.
     * Returns false, without posting or clearing, when ownership was lost.
     */
    suspend fun finish(listId: String, generation: Long, post: (suspend () -> Unit)?): Boolean = mutex.withLock {
        if (!owns(listId, generation)) return@withLock false
        try {
            post?.invoke()
        } finally {
            withContext(NonCancellable) { commitClear() }
        }
        true
    }

    suspend fun shouldOfferPermission(listId: String, permission: PermissionState): Boolean =
        permission == PermissionState.Requestable &&
            prefs.enabled &&
            flags.isEnabled(FLAG_KEY) &&
            clock.now() >= prefs.promptSnoozedUntil &&
            eligibility.isPartlyShopped(listId)

    fun onPermissionRequested() {
        prefs.permissionRequested = true
    }

    fun permissionRequested(): Boolean = prefs.permissionRequested

    suspend fun onPermissionGranted(listId: String): Boolean = arm(listId)

    fun onPermissionDenied() = snoozePrompt()

    fun snoozePrompt() {
        prefs.promptSnoozedUntil = clock.now() + PROMPT_SNOOZE_MILLIS
    }

    suspend fun setEnabled(enabled: Boolean) {
        mutex.withLock {
            prefs.enabled = enabled
            if (!enabled) cancelAndClearLocked()
        }
    }

    suspend fun onFlagsLoaded() {
        mutex.withLock {
            if (!flags.isEnabled(FLAG_KEY)) cancelAndClearLocked()
        }
    }

    fun observeSignOuts(signOuts: Flow<Unit>) {
        scope.launch { signOuts.collect { cancelAndClear() } }
    }

    private suspend fun cancelAndClear() {
        mutex.withLock { cancelAndClearLocked() }
    }

    // ---- Called only with mutex held ----

    private fun gateOpen(): Boolean = prefs.enabled && flags.isEnabled(FLAG_KEY) && gate.notificationsEnabled()

    private suspend fun enqueueLocked(listId: String): Boolean {
        val record = PendingReminder(listId = listId, generation = prefs.generationCounter + 1, armedAt = clock.now())
        val committed = withContext(ioDispatcher) { prefs.commitPending(record) }
        if (!committed) {
            reportCommitFailure()
            return false
        }
        enqueuer.enqueueUnique(listId, record.generation, REMINDER_DELAY_MILLIS)
        return true
    }

    // The clear goes first so that, if the process dies between the two, a
    // surviving job finds no record and does nothing. The cancel runs even when
    // the clear failed: no job means no post, whatever the record says.
    private suspend fun cancelAndClearLocked() {
        commitClear()
        enqueuer.cancelUnique()
    }

    private suspend fun commitClear() {
        if (!withContext(ioDispatcher) { prefs.commitClearPending() }) reportCommitFailure()
    }

    private fun reportCommitFailure() {
        crashReporter.recordNonFatal(
            IllegalStateException(COMMIT_FAILED),
            AppError.DatabaseError(COMMIT_FAILED),
        )
    }

    companion object {
        const val FLAG_KEY = "shopping_reminders"
        const val REMINDER_DELAY_MILLIS = 3L * 60 * 60 * 1000
        const val PROMPT_SNOOZE_MILLIS = 14L * 24 * 60 * 60 * 1000
        private const val COMMIT_FAILED = "reminder prefs commit failed"
    }
}
