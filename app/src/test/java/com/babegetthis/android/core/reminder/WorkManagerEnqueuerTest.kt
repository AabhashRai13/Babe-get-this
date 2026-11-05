package com.babegetthis.android.core.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
        enqueuer = WorkManagerEnqueuer(Lazy { workManager })
    }

    private fun infos() = workManager.getWorkInfosForUniqueWork(WorkManagerEnqueuer.UNIQUE_NAME).get()

    // Constructed while Hilt injects the Application, before WorkManager may be
    // initialised: resolving it then would read workManagerConfiguration early.
    @Test
    fun `constructing the enqueuer does not resolve WorkManager`() {
        var resolved = 0
        val lazyEnqueuer = WorkManagerEnqueuer(
            Lazy {
                resolved++
                workManager
            },
        )
        assertEquals(0, resolved)

        lazyEnqueuer.buildRequest("L1", 1L, 1_000L)
        assertEquals(0, resolved)

        runBlocking { lazyEnqueuer.cancelUnique() }
        assertEquals(1, resolved)
    }

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
        val firstId = infos().single().id

        enqueuer.enqueueUnique("L2", 8L, 3_000L)

        // Under KEEP the second call would be ignored: the first job would stay
        // ENQUEUED and remain the unique name's only entry. Under REPLACE the
        // test driver deletes the replaced job outright; production WorkManager
        // may leave it CANCELLED until pruned. Here it is gone by id and the one
        // live entry is a different job.
        assertNull(workManager.getWorkInfoById(firstId).get())
        val live = infos().single()
        assertNotEquals(firstId, live.id)
        assertEquals(WorkInfo.State.ENQUEUED, live.state)
    }

    @Test
    fun `cancelUnique cancels the pending request`() = runBlocking {
        enqueuer.enqueueUnique("L1", 7L, 3_000L)

        enqueuer.cancelUnique()

        assertEquals(listOf(WorkInfo.State.CANCELLED), infos().map { it.state })
    }
}
