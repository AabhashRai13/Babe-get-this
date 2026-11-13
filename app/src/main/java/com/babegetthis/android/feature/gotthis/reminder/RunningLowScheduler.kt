package com.babegetthis.android.feature.gotthis.reminder

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.feature.gotthis.domain.Days
import com.babegetthis.android.feature.gotthis.domain.ZoneProvider
import dagger.Lazy
import java.util.concurrent.TimeUnit

fun interface RunningLowEnqueuer {
    suspend fun enqueueDaily(initialDelayMillis: Long)
}

/** Makes sure the daily check exists, first firing at the next 18:00 local time. */
class RunningLowScheduler(
    private val enqueuer: RunningLowEnqueuer,
    private val clock: ReminderClock,
    private val zone: ZoneProvider,
) {

    suspend fun ensureScheduled() {
        val tz = zone.current()
        val now = clock.now()
        val today = Days.epochDay(now, tz)
        var first = Days.millisAtHour(today, CHECK_HOUR, tz)
        if (first <= now) first = Days.millisAtHour(today + 1, CHECK_HOUR, tz)
        enqueuer.enqueueDaily(first - now)
    }

    private companion object {
        const val CHECK_HOUR = 18
    }
}

/**
 * The WorkManager side. Lazy for the same reason as WorkManagerEnqueuer: building
 * it while Hilt injects the Application must not initialise WorkManager.
 */
class WorkManagerRunningLowEnqueuer(private val workManager: Lazy<WorkManager>) : RunningLowEnqueuer {

    override suspend fun enqueueDaily(initialDelayMillis: Long) {
        workManager.get().enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            buildRequest(initialDelayMillis),
        ).await()
    }

    internal fun buildRequest(initialDelayMillis: Long): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<RunningLowWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

    companion object {
        const val UNIQUE_NAME = "got_this_daily"
    }
}
