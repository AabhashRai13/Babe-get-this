package com.babegetthis.android.core.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.reminder.ReminderScheduler.Companion.FLAG_KEY
import com.babegetthis.android.core.reminder.ReminderScheduler.Companion.REMINDER_DELAY_MILLIS
import com.babegetthis.android.core.reminder.ReminderWorker.Companion.KEY_GENERATION
import com.babegetthis.android.core.reminder.ReminderWorker.Companion.KEY_LIST_ID
import com.babegetthis.android.core.reminder.ReminderWorker.Companion.MAX_AGE_MILLIS
import com.babegetthis.android.core.reminder.data.local.PendingReminder
import com.babegetthis.android.core.reminder.data.local.ReminderContent
import com.babegetthis.android.core.reminder.data.local.ReminderContentReader
import com.babegetthis.android.core.reminder.data.local.ReminderEligibility
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.testing.FakeClock
import com.babegetthis.android.testing.FakeForegroundState
import com.babegetthis.android.testing.FakeNotificationGate
import com.babegetthis.android.testing.FakeReminderPrefs
import com.babegetthis.android.testing.FakeWorkEnqueuer
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.TestData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The worker runs over the real ReminderScheduler (so owns/finish and the lock
// are the committed ones) and a real in-memory database (so the snapshot is
// what Room actually holds at fire time). Only the platform seams are fakes.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReminderWorkerTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val enqueuer = FakeWorkEnqueuer()
    private val prefs = FakeReminderPrefs()
    private val flags = FeatureFlagCache().apply { update(mapOf(FLAG_KEY to true)) }
    private val gate = FakeNotificationGate()
    private val foreground = FakeForegroundState()
    private val clock = FakeClock()
    private val analytics = mockk<AnalyticsRepository>(relaxed = true)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    // The scheduler reads eligibility through its own, unhooked reader; the
    // worker gets a spy so a test can hook or break its snapshot without the
    // hook re-entering the scheduler's lock through arm's eligibility check.
    private val schedulerReader by lazy { ReminderContentReader(dbRule.db) }
    private val workerReader by lazy { spyk(ReminderContentReader(dbRule.db)) }

    // ReminderNotifier is final, so the recording stand-in is a spy whose post
    // records instead of reaching NotificationManager; text() stays real.
    private val posted = mutableListOf<ReminderContent>()
    private val notifier = spyk(ReminderNotifier(context, NotificationManagerCompat.from(context))).also {
        every { it.post(any()) } answers { posted += firstArg<ReminderContent>() }
    }

    private lateinit var scheduler: ReminderScheduler

    private val factory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = ReminderWorker(
            context = appContext,
            params = workerParameters,
            scheduler = scheduler,
            prefs = prefs,
            reader = workerReader,
            notifier = notifier,
            gate = gate,
            foreground = foreground,
            clock = clock,
            analytics = analytics,
            crashReporter = crashReporter,
        )
    }

    // Same shape as ReminderSchedulerTest: the io dispatcher is a
    // StandardTestDispatcher on runTest's scheduler, so prefs commits are real
    // suspension points.
    private fun TestScope.newScheduler() {
        scheduler = ReminderScheduler(
            enqueuer = enqueuer,
            prefs = prefs,
            eligibility = ReminderEligibility(schedulerReader),
            flags = flags,
            gate = gate,
            crashReporter = crashReporter,
            clock = clock,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    // Milk picked, Eggs and Bread not: partly shopped.
    private suspend fun seedPartlyShopped(listId: String = "L1", isLocked: Boolean = false) {
        dbRule.listDao.insertList(TestData.listEntity(id = listId, name = "Weekly", isLocked = isLocked))
        dbRule.itemDao.insertItem(
            TestData.itemEntity(id = "$listId-a", listId = listId, name = "Milk", isPickedUp = true),
        )
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "$listId-b", listId = listId, name = "Eggs"))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "$listId-c", listId = listId, name = "Bread"))
    }

    // Seeds L1, arms it through the scheduler at ARMED_AT (generation 1), and
    // moves the clock to the moment the job would fire.
    private suspend fun TestScope.armL1(isLocked: Boolean = false) {
        newScheduler()
        seedPartlyShopped("L1", isLocked)
        clock.now = ARMED_AT
        assertTrue(scheduler.arm("L1"))
        assertEquals(PendingReminder("L1", 1L, ARMED_AT), prefs.readPending())
        clock.now = ARMED_AT + REMINDER_DELAY_MILLIS
    }

    private suspend fun runWorker(listId: String? = "L1", generation: Long? = 1L): ListenableWorker.Result {
        val input = buildList<Pair<String, Any>> {
            listId?.let { add(KEY_LIST_ID to it) }
            generation?.let { add(KEY_GENERATION to it) }
        }
        val worker = TestListenableWorkerBuilder<ReminderWorker>(context)
            .setInputData(workDataOf(*input.toTypedArray()))
            .setWorkerFactory(factory)
            .build()
        return worker.doWork()
    }

    private fun assertPostedNothing() {
        assertTrue("nothing may be posted", posted.isEmpty())
        verify(exactly = 0) { analytics.track(any()) }
    }

    private fun assertCleared() {
        assertNull(prefs.readPending())
        assertEquals("clear", prefs.commitLog.last())
    }

    // ---- The post ----

    @Test
    fun `posts when partly shopped and ownership holds, then the record is cleared`() = runTest {
        armL1()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(1, posted.size)
        val content = posted.single()
        assertEquals("L1", content.listId)
        assertEquals("Weekly", content.listName)
        assertEquals(1, content.pickedCount)
        assertEquals(listOf("Bread", "Eggs"), content.uncheckedNames.sorted())
        verify(exactly = 1) { analytics.track(AnalyticsEvent.ReminderPosted(REMINDER_DELAY_MILLIS)) }
        assertEquals(listOf("pending:L1:1", "clear"), prefs.commitLog)
        assertNull(prefs.readPending())
        verify(exactly = 0) { crashReporter.recordNonFatal(any(), any()) }
    }

    @Test
    fun `missing list id or negative generation returns success and touches nothing`() = runTest {
        armL1()

        assertEquals(ListenableWorker.Result.success(), runWorker(listId = null))
        assertEquals(ListenableWorker.Result.success(), runWorker(listId = "  "))
        assertEquals(ListenableWorker.Result.success(), runWorker(generation = null))
        assertEquals(ListenableWorker.Result.success(), runWorker(generation = -1L))

        assertPostedNothing()
        assertEquals(PendingReminder("L1", 1L, ARMED_AT), prefs.readPending())
        assertEquals(listOf("pending:L1:1"), prefs.commitLog)
    }

    @Test
    fun `a cleared record with the counter still equal to the generation posts nothing`() = runTest {
        armL1()
        assertTrue(prefs.commitClearPending())
        assertEquals(1L, prefs.generationCounter)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        assertEquals(listOf("pending:L1:1", "clear"), prefs.commitLog)
    }

    // ---- Checks 2 to 6: each posts nothing and clears the record ----

    @Test
    fun `enabled false posts nothing and clears`() = runTest {
        armL1()
        prefs.enabled = false

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        assertCleared()
    }

    @Test
    fun `os notifications disabled posts nothing and clears`() = runTest {
        armL1()
        gate.enabled = false

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        assertCleared()
    }

    @Test
    fun `foreground posts nothing and clears`() = runTest {
        armL1()
        foreground.foreground = true

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        assertCleared()
    }

    @Test
    fun `negative age posts nothing`() = runTest {
        armL1()
        clock.now = ARMED_AT - 1

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        assertCleared()
    }

    @Test
    fun `age of 24h plus one second posts nothing`() = runTest {
        armL1()
        clock.now = ARMED_AT + MAX_AGE_MILLIS + 1_000L

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        assertCleared()
        assertEquals(24L * 60 * 60 * 1000, MAX_AGE_MILLIS)
    }

    @Test
    fun `list finished posts nothing and clears`() = runTest {
        armL1()
        dbRule.itemDao.updatePickedUpStatus("L1-b", isPickedUp = true, updatedAt = TestData.T0, pendingSync = false)
        dbRule.itemDao.updatePickedUpStatus("L1-c", isPickedUp = true, updatedAt = TestData.T0, pendingSync = false)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        assertCleared()
    }

    @Test
    fun `a deleted list posts nothing and clears`() = runTest {
        armL1()
        dbRule.listDao.insertList(TestData.listEntity(id = "L1", name = "Weekly").copy(deletedAt = TestData.T0))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        assertCleared()
    }

    // ---- Fire-time content ----

    @Test
    fun `a partner tick written through the dao still posts`() = runTest {
        armL1()
        // A synced tick lands in Room without passing through the kicker, so
        // the record is untouched and the snapshot is the only place it shows.
        dbRule.itemDao.updatePickedUpStatus("L1-b", isPickedUp = true, updatedAt = TestData.T0, pendingSync = false)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(1, posted.size)
        assertEquals(2, posted.single().pickedCount)
        assertEquals(listOf("Bread"), posted.single().uncheckedNames)
        assertNull(prefs.readPending())
    }

    @Test
    fun `a retarget between snapshot and finish posts nothing and keeps B`() = runTest {
        armL1()
        seedPartlyShopped("L2")
        val armedL2At = clock.now
        // Outside the lock: the snapshot has been taken, finish has not yet run.
        // The real read goes through the unhooked reader over the same database.
        coEvery { workerReader.snapshot("L1") } coAnswers {
            val content = schedulerReader.snapshot(firstArg())
            assertTrue(scheduler.arm("L2"))
            content
        }

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        assertEquals(PendingReminder("L2", 2L, armedL2At), prefs.readPending())
        assertEquals(listOf("pending:L1:1", "pending:L2:2"), prefs.commitLog)
        assertEquals(Triple("L2", 2L, REMINDER_DELAY_MILLIS), enqueuer.enqueued.last())
    }

    @Test
    fun `a locked list withholds names`() = runTest {
        armL1(isLocked = true)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val content = posted.single()
        assertTrue(content.isLocked)
        assertEquals("2 items are still unticked", notifier.text(content).body)
    }

    // ---- Failures ----

    @Test
    fun `CancellationException propagates and is not reported`() = runTest {
        armL1()
        val cancelled = CancellationException("stopped")
        coEvery { workerReader.snapshot(any()) } throws cancelled

        try {
            runWorker()
            fail("CancellationException must propagate")
        } catch (e: CancellationException) {
            assertSame(cancelled, e)
        }

        assertPostedNothing()
        verify(exactly = 0) { crashReporter.recordNonFatal(any(), any()) }
    }

    @Test
    fun `other failures report a non-fatal and return success`() = runTest {
        armL1()
        val boom = IllegalStateException("db gone")
        coEvery { workerReader.snapshot(any()) } throws boom

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertPostedNothing()
        verify(exactly = 1) { crashReporter.recordNonFatal(boom, AppError.UnknownError("reminder worker")) }
    }

    private companion object {
        const val ARMED_AT = TestData.T0
    }
}
