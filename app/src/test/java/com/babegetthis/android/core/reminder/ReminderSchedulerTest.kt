package com.babegetthis.android.core.reminder

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.reminder.ReminderScheduler.Companion.FLAG_KEY
import com.babegetthis.android.core.reminder.ReminderScheduler.Companion.PROMPT_SNOOZE_MILLIS
import com.babegetthis.android.core.reminder.ReminderScheduler.Companion.REMINDER_DELAY_MILLIS
import com.babegetthis.android.core.reminder.data.local.PendingReminder
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.testing.FakeClock
import com.babegetthis.android.testing.FakeEligibility
import com.babegetthis.android.testing.FakeNotificationGate
import com.babegetthis.android.testing.FakeReminderPrefs
import com.babegetthis.android.testing.FakeWorkEnqueuer
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSchedulerTest {

    private val enqueuer = FakeWorkEnqueuer()
    private val prefs = FakeReminderPrefs()
    private val eligibility = FakeEligibility()
    private val flags = FeatureFlagCache().apply { update(mapOf(FLAG_KEY to true)) }
    private val gate = FakeNotificationGate()
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val clock = FakeClock(now = 1_000L)

    private lateinit var schedulerScope: CoroutineScope

    // The launch scope and the io dispatcher are two StandardTestDispatchers on
    // runTest's scheduler, so advanceUntilIdle drives the kicker's launches
    // (backgroundScope work is skipped by it) and every prefs commit is a real
    // suspension point that lets other coroutines run and contend for the lock.
    private fun TestScope.newScheduler(): ReminderScheduler {
        schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        return ReminderScheduler(
            enqueuer = enqueuer,
            prefs = prefs,
            eligibility = eligibility.eligibility,
            flags = flags,
            gate = gate,
            crashReporter = crashReporter,
            clock = clock,
            scope = schedulerScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    // ---- Arming and resetting ----

    @Test
    fun `a tick on a partly shopped list arms with the list, 3h delay and generation 1`() = runTest {
        val scheduler = newScheduler()

        scheduler.onLocalPickUp("L1")
        assertTrue("onLocalPickUp must not run the work on the caller", enqueuer.enqueued.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf(Triple("L1", 1L, REMINDER_DELAY_MILLIS)), enqueuer.enqueued)
        assertEquals(3L * 60 * 60 * 1000, REMINDER_DELAY_MILLIS)
        assertEquals(PendingReminder("L1", 1L, 1_000L), prefs.readPending())
    }

    @Test
    fun `a second tick on the same list re-enqueues with generation 2`() = runTest {
        val scheduler = newScheduler()

        scheduler.onLocalPickUp("L1")
        advanceUntilIdle()
        clock.now = 2_000L
        scheduler.onLocalPickUp("L1")
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), enqueuer.enqueued.map { it.second })
        assertEquals(PendingReminder("L1", 2L, 2_000L), prefs.readPending())
    }

    @Test
    fun `a tick on another partly shopped list retargets with generation 3`() = runTest {
        val scheduler = newScheduler()

        scheduler.onLocalPickUp("L1")
        advanceUntilIdle()
        scheduler.onLocalPickUp("L1")
        advanceUntilIdle()
        scheduler.onLocalPickUp("L2")
        advanceUntilIdle()

        assertEquals(Triple("L2", 3L, REMINDER_DELAY_MILLIS), enqueuer.enqueued.last())
        assertEquals("L2", prefs.readPending()?.listId)
        assertEquals(3L, prefs.readPending()?.generation)
    }

    @Test
    fun `a tick that leaves its list not partly shopped does not arm and keeps the other list's record`() = runTest {
        val scheduler = newScheduler()
        scheduler.onLocalPickUp("L1")
        advanceUntilIdle()

        eligibility.partlyShopped = { it == "L1" }
        scheduler.onLocalPickUp("L2")
        advanceUntilIdle()

        assertFalse(scheduler.arm("L2"))
        assertEquals(1, enqueuer.enqueued.size)
        assertEquals(0, enqueuer.cancels)
        assertEquals(PendingReminder("L1", 1L, 1_000L), prefs.readPending())
    }

    @Test
    fun `an edit with nothing pending does not arm`() = runTest {
        val scheduler = newScheduler()

        scheduler.onLocalEdit("L1")
        advanceUntilIdle()

        assertTrue(enqueuer.enqueued.isEmpty())
        assertNull(prefs.readPending())
    }

    @Test
    fun `an edit on the pending list re-enqueues with a new generation`() = runTest {
        val scheduler = newScheduler()
        scheduler.onLocalPickUp("L1")
        advanceUntilIdle()

        clock.now = 5_000L
        scheduler.onLocalEdit("L1")
        assertEquals("onLocalEdit must not run the work on the caller", 1, enqueuer.enqueued.size)
        advanceUntilIdle()

        assertEquals(Triple("L1", 2L, REMINDER_DELAY_MILLIS), enqueuer.enqueued.last())
        assertEquals(PendingReminder("L1", 2L, 5_000L), prefs.readPending())
    }

    @Test
    fun `an edit on a different list changes nothing`() = runTest {
        val scheduler = newScheduler()
        scheduler.onLocalPickUp("L1")
        advanceUntilIdle()

        scheduler.onLocalEdit("L2")
        advanceUntilIdle()

        assertEquals(1, enqueuer.enqueued.size)
        assertEquals(0, enqueuer.cancels)
        assertEquals(PendingReminder("L1", 1L, 1_000L), prefs.readPending())
    }

    // ---- Gate ----

    @Test
    fun `enabled false blocks arming and cancels and clears`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))

        scheduler.setEnabled(false)

        assertFalse(prefs.enabled)
        assertEquals(1, enqueuer.cancels)
        assertNull(prefs.readPending())

        assertFalse(scheduler.arm("L1"))
        assertEquals(2, enqueuer.cancels)
        assertEquals(1, enqueuer.enqueued.size)
        assertNull(prefs.readPending())
    }

    @Test
    fun `setEnabled true stores the preference without cancelling`() = runTest {
        val scheduler = newScheduler()
        prefs.enabled = false

        scheduler.setEnabled(true)

        assertTrue(prefs.enabled)
        assertEquals(0, enqueuer.cancels)
        assertTrue(scheduler.arm("L1"))
    }

    @Test
    fun `flag off blocks arming and cancels and clears`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))

        flags.update(mapOf(FLAG_KEY to false))

        assertFalse(scheduler.arm("L2"))
        assertEquals(1, enqueuer.cancels)
        assertEquals(1, enqueuer.enqueued.size)
        assertNull(prefs.readPending())
    }

    @Test
    fun `os notifications disabled blocks arming and cancels and clears`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))

        gate.enabled = false

        assertFalse(scheduler.arm("L1"))
        assertEquals(1, enqueuer.cancels)
        assertEquals(1, enqueuer.enqueued.size)
        assertNull(prefs.readPending())
    }

    @Test
    fun `an edit on the pending list with the gate closed cancels and clears`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))

        gate.enabled = false
        scheduler.reset("L1")

        assertEquals(1, enqueuer.cancels)
        assertEquals(1, enqueuer.enqueued.size)
        assertNull(prefs.readPending())
    }

    @Test
    fun `onFlagsLoaded with the flag off cancels and clears, with it on does nothing`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))

        scheduler.onFlagsLoaded()
        assertEquals(0, enqueuer.cancels)
        assertEquals(PendingReminder("L1", 1L, 1_000L), prefs.readPending())

        flags.update(emptyMap())
        scheduler.onFlagsLoaded()
        assertEquals(1, enqueuer.cancels)
        assertNull(prefs.readPending())
    }

    @Test
    fun `observeSignOuts cancels and clears on each emission`() = runTest {
        val scheduler = newScheduler()
        val signOuts = MutableSharedFlow<Unit>()
        scheduler.observeSignOuts(signOuts)
        runCurrent()

        assertTrue(scheduler.arm("L1"))
        signOuts.emit(Unit)
        advanceUntilIdle()
        assertEquals(1, enqueuer.cancels)
        assertNull(prefs.readPending())

        assertTrue(scheduler.arm("L2"))
        signOuts.emit(Unit)
        advanceUntilIdle()
        assertEquals(2, enqueuer.cancels)
        assertNull(prefs.readPending())

        schedulerScope.cancel() // stops the collector
    }

    // ---- Durability and ordering ----

    @Test
    fun `the record is committed before the enqueuer is called`() = runTest {
        val scheduler = newScheduler()
        var generationAtEnqueue: Long? = null
        enqueuer.onEnqueue = { generationAtEnqueue = prefs.readPending()?.generation }

        assertTrue(scheduler.arm("L1"))

        assertEquals(1L, generationAtEnqueue)
    }

    @Test
    fun `a failed commit means no enqueue, a non-fatal and no record`() = runTest {
        val scheduler = newScheduler()
        prefs.failCommits = true

        assertFalse(scheduler.arm("L1"))

        assertTrue(enqueuer.enqueued.isEmpty())
        assertNull(prefs.readPending())
        verify(exactly = 1) {
            crashReporter.recordNonFatal(
                any<IllegalStateException>(),
                AppError.DatabaseError("reminder prefs commit failed"),
            )
        }
    }

    @Test
    fun `a failed clear still cancels and reports a non-fatal`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))
        prefs.failCommits = true

        scheduler.setEnabled(false)

        assertEquals(1, enqueuer.cancels)
        verify(exactly = 1) {
            crashReporter.recordNonFatal(
                any<IllegalStateException>(),
                AppError.DatabaseError("reminder prefs commit failed"),
            )
        }
    }

    @Test
    fun `a second tick waits for the first enqueue to complete`() = runTest {
        val scheduler = newScheduler()
        val hold = CompletableDeferred<Unit>()
        enqueuer.gate = hold

        scheduler.onLocalPickUp("L1")
        runCurrent()
        scheduler.onLocalPickUp("L1")
        runCurrent()

        assertEquals(1, enqueuer.enqueued.size)
        assertEquals(1L, prefs.readPending()?.generation)

        hold.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, enqueuer.enqueued.size)
        assertEquals(2L, prefs.readPending()?.generation)
    }

    @Test
    fun `fifty interleaved ticks and edits leave one job whose generation matches the record`() = runTest {
        val scheduler = newScheduler()

        repeat(50) { i ->
            launch {
                when (i % 4) {
                    0 -> scheduler.arm("L1")
                    1 -> scheduler.arm("L2")
                    2 -> scheduler.reset("L1")
                    else -> scheduler.reset("L2")
                }
            }
        }
        advanceUntilIdle()

        val record = prefs.readPending()!!
        assertEquals(enqueuer.enqueued.last().second, prefs.generationCounter)
        assertEquals(prefs.generationCounter, record.generation)
        assertEquals(enqueuer.enqueued.last().first, record.listId)
        // Every enqueue got a fresh generation, in commit order: no two
        // coroutines ever read the same counter.
        assertEquals((1L..enqueuer.enqueued.size).toList(), enqueuer.enqueued.map { it.second })
    }

    // ---- Ownership and finish ----

    @Test
    fun `owns is true only for the active record`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))

        assertTrue(scheduler.owns("L1", 1L))
        assertFalse(scheduler.owns("L2", 1L))
        assertFalse(scheduler.owns("L1", 2L))

        assertTrue(scheduler.finish("L1", 1L, null))
        assertEquals(1L, prefs.generationCounter)
        assertFalse(scheduler.owns("L1", 1L))
    }

    @Test
    fun `finish with ownership runs the post and clears`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))
        var posts = 0

        assertTrue(scheduler.finish("L1", 1L) { posts++ })

        assertEquals(1, posts)
        assertNull(prefs.readPending())
    }

    @Test
    fun `finish without ownership neither posts nor clears`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))
        var posts = 0

        assertFalse(scheduler.finish("L1", 7L) { posts++ })
        assertFalse(scheduler.finish("L2", 1L) { posts++ })

        assertEquals(0, posts)
        assertEquals(PendingReminder("L1", 1L, 1_000L), prefs.readPending())
        assertEquals(listOf("pending:L1:1"), prefs.commitLog)
    }

    @Test
    fun `finish with a null post clears without posting`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))

        assertTrue(scheduler.finish("L1", 1L, null))

        assertNull(prefs.readPending())
        assertEquals(listOf("pending:L1:1", "clear"), prefs.commitLog)
    }

    @Test
    fun `finish clears even when the post throws, and rethrows`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))
        val boom = IllegalStateException("post failed")

        try {
            scheduler.finish("L1", 1L) { throw boom }
            fail("expected the post's exception to propagate")
        } catch (e: IllegalStateException) {
            assertEquals(boom, e)
        }

        assertNull(prefs.readPending())
    }

    @Test
    fun `finish clears even when cancelled during the post`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))

        val job = launch { scheduler.finish("L1", 1L) { awaitCancellation() } }
        runCurrent()
        assertNotNull(prefs.readPending())

        job.cancel()
        advanceUntilIdle()

        assertNull(prefs.readPending())
    }

    @Test
    fun `a retarget launched while the post is suspended waits, then writes B`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))
        val postGate = CompletableDeferred<Unit>()
        var posts = 0

        val finishing = async {
            scheduler.finish("L1", 1L) {
                posts++
                postGate.await()
            }
        }
        runCurrent()
        assertEquals(1, posts)

        val retarget = async { scheduler.arm("L2") }
        runCurrent()

        assertEquals(1, enqueuer.enqueued.size)
        assertEquals(PendingReminder("L1", 1L, 1_000L), prefs.readPending())

        postGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(finishing.await())
        assertTrue(retarget.await())
        assertEquals(1, posts)
        assertEquals("L2", prefs.readPending()?.listId)
        assertEquals(2L, prefs.readPending()?.generation)
        assertEquals(2, enqueuer.enqueued.size)
        assertEquals(listOf("pending:L1:1", "clear", "pending:L2:2"), prefs.commitLog)
    }

    @Test
    fun `a retarget that completes first makes finish for A a no-op`() = runTest {
        val scheduler = newScheduler()
        assertTrue(scheduler.arm("L1"))
        assertTrue(scheduler.arm("L2"))
        var posts = 0

        assertFalse(scheduler.finish("L1", 1L) { posts++ })

        assertEquals(0, posts)
        assertEquals("L2", prefs.readPending()?.listId)
        assertEquals(2L, prefs.readPending()?.generation)
    }

    // ---- Prompt ----

    @Test
    fun `shouldOfferPermission is true only when Requestable, enabled, flag on, not snoozed and partly shopped`() =
        runTest {
            val scheduler = newScheduler()
            val requestable = PermissionState.Requestable

            assertTrue(scheduler.shouldOfferPermission("L1", requestable))

            for (other in PermissionState.entries - requestable) {
                assertFalse("permission $other", scheduler.shouldOfferPermission("L1", other))
            }

            prefs.enabled = false
            assertFalse("disabled", scheduler.shouldOfferPermission("L1", requestable))
            prefs.enabled = true

            flags.update(emptyMap())
            assertFalse("flag off", scheduler.shouldOfferPermission("L1", requestable))
            flags.update(mapOf(FLAG_KEY to true))

            prefs.promptSnoozedUntil = clock.now + 1
            assertFalse("snoozed", scheduler.shouldOfferPermission("L1", requestable))
            prefs.promptSnoozedUntil = 0L

            eligibility.partlyShopped = { false }
            assertFalse("not partly shopped", scheduler.shouldOfferPermission("L1", requestable))
            eligibility.partlyShopped = { true }

            assertTrue(scheduler.shouldOfferPermission("L1", requestable))
        }

    @Test
    fun `the snooze boundary is inclusive at exactly 14 days`() = runTest {
        val scheduler = newScheduler()
        scheduler.snoozePrompt()
        val snoozedUntil = prefs.promptSnoozedUntil
        assertEquals(1_000L + 14L * 24 * 60 * 60 * 1000, snoozedUntil)

        clock.now = snoozedUntil - 1
        assertFalse(scheduler.shouldOfferPermission("L1", PermissionState.Requestable))

        clock.now = snoozedUntil
        assertTrue(scheduler.shouldOfferPermission("L1", PermissionState.Requestable))
    }

    @Test
    fun `onPermissionDenied snoozes for 14 days`() = runTest {
        val scheduler = newScheduler()
        clock.now = 5L

        scheduler.onPermissionDenied()

        assertEquals(5L + PROMPT_SNOOZE_MILLIS, prefs.promptSnoozedUntil)
        assertEquals(14L * 24 * 60 * 60 * 1000, PROMPT_SNOOZE_MILLIS)
    }

    @Test
    fun `onPermissionRequested is remembered`() = runTest {
        val scheduler = newScheduler()
        assertFalse(scheduler.permissionRequested())

        scheduler.onPermissionRequested()

        assertTrue(prefs.permissionRequested)
        assertTrue(scheduler.permissionRequested())
    }

    @Test
    fun `onPermissionGranted arms only while still partly shopped`() = runTest {
        val scheduler = newScheduler()

        eligibility.partlyShopped = { false }
        assertFalse(scheduler.onPermissionGranted("L1"))
        assertTrue(enqueuer.enqueued.isEmpty())

        eligibility.partlyShopped = { true }
        assertTrue(scheduler.onPermissionGranted("L1"))
        assertEquals(listOf(Triple("L1", 1L, REMINDER_DELAY_MILLIS)), enqueuer.enqueued)
    }
}
