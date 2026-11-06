package com.babegetthis.android.core.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.reminder.data.local.ReminderContentReader
import com.babegetthis.android.core.reminder.data.local.ReminderPrefs
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * The reminder job's body. Everything is re-checked at fire time and any
 * doubt means silence: every path returns [Result.success], so WorkManager
 * never retries a reminder.
 *
 * Once the job is known to own the record, a check that fails clears the
 * record through [ReminderScheduler.finish] so a stale record cannot re-arm on
 * a later edit. The post itself runs inside [ReminderScheduler.finish], which
 * repeats the ownership check under the scheduler's lock, so a retarget that
 * lands after the snapshot posts nothing.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: ReminderScheduler,
    private val prefs: ReminderPrefs,
    private val reader: ReminderContentReader,
    private val notifier: ReminderNotifier,
    private val gate: NotificationGate,
    private val foreground: ForegroundState,
    private val clock: ReminderClock,
    private val analytics: AnalyticsRepository,
    private val crashReporter: CrashReporter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val listId = inputData.getString(KEY_LIST_ID)
        val generation = inputData.getLong(KEY_GENERATION, -1L)
        // A malformed job (e.g. from an older build) owns nothing: no finish,
        // so it cannot clear a record some other job owns.
        if (listId.isNullOrBlank() || generation < 0) return Result.success()
        try {
            fire(listId, generation)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashReporter.recordNonFatal(e, AppError.UnknownError(FAILED))
        }
        return Result.success()
    }

    private suspend fun fire(listId: String, generation: Long) {
        // Check 1 (ownership), unlocked, in ONE read: a second read after a
        // separate owns() could find the record cleared or retargeted in
        // between. finish() repeats the check under the lock.
        val pending = prefs.readPending()?.takeIf { it.listId == listId && it.generation == generation }
            ?: return
        val age = clock.now() - pending.armedAt
        // Checks 2 to 5 short-circuit before the snapshot, so Room is read
        // only when nothing cheaper has already said no.
        val content = if (checksPass(age)) reader.snapshot(listId)?.takeIf { it.isPartlyShopped } else null
        if (content == null) {
            scheduler.finish(listId, generation, null)
            return
        }
        // Runs under the scheduler's lock: post and track only. Calling any
        // locked scheduler function from here would deadlock.
        scheduler.finish(listId, generation) {
            notifier.post(content)
            analytics.track(AnalyticsEvent.ReminderPosted(age))
        }
    }

    private fun checksPass(age: Long): Boolean = prefs.enabled &&
        gate.notificationsEnabled() &&
        !foreground.isForeground() &&
        age in 0..MAX_AGE_MILLIS

    companion object {
        const val KEY_LIST_ID = "list_id"
        const val KEY_GENERATION = "generation"
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
        private const val FAILED = "reminder worker"
    }
}
