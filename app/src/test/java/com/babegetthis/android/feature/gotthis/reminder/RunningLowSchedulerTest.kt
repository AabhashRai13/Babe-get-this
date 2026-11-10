package com.babegetthis.android.feature.gotthis.reminder

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.babegetthis.android.core.reminder.ReminderClock
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RunningLowSchedulerTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 3_600_000L

    // 2026-11-01 00:00 UTC
    private val midnight = 1_793_491_200_000L

    private fun delayAt(hourOfDay: Int): Long {
        val delays = mutableListOf<Long>()
        val scheduler = RunningLowScheduler({ delays += it }, ReminderClock { midnight + hourOfDay * hour }, { utc })
        runTest { scheduler.ensureScheduled() }
        return delays.single()
    }

    @Test
    fun `at 17 00 the first run is an hour away`() {
        assertEquals(hour, delayAt(17))
    }

    @Test
    fun `at 19 00 the first run is tomorrow at 18 00`() {
        assertEquals(23 * hour, delayAt(19))
    }

    @Test
    fun `at exactly 18 00 the first run is tomorrow`() {
        assertEquals(24 * hour, delayAt(18))
    }

    @Test
    fun `the work manager enqueuer keeps one daily periodic request`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = WorkManager.getInstance(context)
        val enqueuer = WorkManagerRunningLowEnqueuer(Lazy { workManager })

        enqueuer.enqueueDaily(5 * hour)
        val first = workManager.getWorkInfosForUniqueWork(WorkManagerRunningLowEnqueuer.UNIQUE_NAME).get().single()
        enqueuer.enqueueDaily(9 * hour)
        val after = workManager.getWorkInfosForUniqueWork(WorkManagerRunningLowEnqueuer.UNIQUE_NAME).get().single()

        assertEquals("got_this_daily", WorkManagerRunningLowEnqueuer.UNIQUE_NAME)
        assertEquals(first.id, after.id)
        assertEquals(WorkInfo.State.ENQUEUED, after.state)
        val request = WorkManagerRunningLowEnqueuer(Lazy { workManager }).buildRequest(5 * hour)
        assertEquals(TimeUnit.HOURS.toMillis(24), request.workSpec.intervalDuration)
        assertEquals(5 * hour, request.workSpec.initialDelay)
    }
}
