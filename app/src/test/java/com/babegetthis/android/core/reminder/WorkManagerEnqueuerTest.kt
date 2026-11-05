package com.babegetthis.android.core.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The manifest removes WorkManager's auto-initializer, and Robolectric's
// application is a plain Application, so each test initialises test WorkManager.
@RunWith(RobolectricTestRunner::class)
class WorkManagerEnqueuerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var enqueuer: WorkManagerEnqueuer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        enqueuer = WorkManagerEnqueuer(workManager)
    }

    private fun infos() = workManager.getWorkInfosForUniqueWork(WorkManagerEnqueuer.UNIQUE_NAME).get()

    @Test
    fun `buildRequest carries list id, generation as a Long and the delay`() {
        val request = enqueuer.buildRequest("L1", 7L, 3_000L)

        assertEquals("L1", request.workSpec.input.getString(ReminderWorker.KEY_LIST_ID))
        assertEquals(7L, request.workSpec.input.getLong(ReminderWorker.KEY_GENERATION, -1L))
        assertEquals(3_000L, request.workSpec.initialDelay)
    }

    @Test
    fun `enqueueUnique keeps exactly one request and replaces it`() = runBlocking {
        enqueuer.enqueueUnique("L1", 7L, 3_000L)
        assertEquals(1, infos().size)

        enqueuer.enqueueUnique("L2", 8L, 3_000L)
        val live = infos().filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, live.size)
    }

    @Test
    fun `cancelUnique cancels the pending request`() = runBlocking {
        enqueuer.enqueueUnique("L1", 7L, 3_000L)

        enqueuer.cancelUnique()

        assertEquals(listOf(WorkInfo.State.CANCELLED), infos().map { it.state })
    }
}
