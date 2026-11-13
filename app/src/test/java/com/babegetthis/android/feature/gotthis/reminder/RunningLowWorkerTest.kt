package com.babegetthis.android.feature.gotthis.reminder

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.ReminderIntentParser
import com.babegetthis.android.core.sync.SyncKicker
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.feature.gotthis.data.GotThisAccess
import com.babegetthis.android.feature.gotthis.data.GotThisRepository
import com.babegetthis.android.feature.gotthis.data.StoredOwner
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ReminderFollowUpEntity
import com.babegetthis.android.feature.gotthis.data.remote.GotThisRemote
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.feature.gotthis.domain.ZoneProvider
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeClock
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.FakeGotThisRemote
import com.babegetthis.android.testing.FakeNotificationGate
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.inMemoryTokenManager
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class RunningLowWorkerTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val day = 86_400_000L
    private val hour = 3_600_000L

    // 2026-11-01 00:00 UTC (a Sunday). Offsets below are whole days from it.
    private val nov1 = 1_793_491_200_000L

    // Friday 2026-11-06 18:00 UTC, the eve of Saturday and in ISO week 2026-W45.
    private val fridayEvening = nov1 + 5 * day + 18 * hour
    private val week = "2026-W45"

    // Account 2222... is outside the holdout (number 12); 1111... is inside (number 2).
    private val user = "22222222-2222-2222-2222-222222222222"
    private val holdoutUser = "11111111-1111-1111-1111-111111111111"

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock(fridayEvening)
    private val prefs = FakeGotThisPrefs()
    private val remote = FakeGotThisRemote().apply { sessionUser = user }
    private val gate = FakeNotificationGate()
    private val analytics = mockk<AnalyticsRepository>(relaxed = true)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val utc = TimeZone.getTimeZone("UTC")
    private val zone = ZoneProvider { utc }

    private var storedOwner = StoredOwner { user }
    private var notificationGate: NotificationGate = gate
    private var remoteInUse: GotThisRemote = remote
    private var seq = 0

    private val repository by lazy {
        GotThisRepository(dbRule.db, prefs, SyncKicker { }, clock, { utc })
    }

    private val factory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = RunningLowWorker(
            context = appContext,
            params = workerParameters,
            db = dbRule.db,
            repository = repository,
            prefs = prefs,
            access = GotThisAccess(FeatureFlagCache(), prefs),
            remote = remoteInUse,
            storedOwner = storedOwner,
            notifier = RunningLowNotifier(context, NotificationManagerCompat.from(context)),
            notificationGate = notificationGate,
            analytics = analytics,
            crashReporter = crashReporter,
            clock = clock,
            zone = zone,
        )
    }

    private fun run(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<RunningLowWorker>(context).setWorkerFactory(factory).build().doWork()
    }

    private fun owner() = storedOwner.ownerId()

    // A purchase on each of the given days (offsets from 2026-11-01 00:00 UTC), at 10:00.
    private suspend fun buy(name: String, vararg dayOffsets: Int, account: String? = owner()) {
        dbRule.purchaseDao.upsert(
            dayOffsets.map {
                seq++
                PurchaseEntity(
                    id = "p$seq",
                    tickId = "t$seq",
                    accountId = account,
                    sourceItemId = "i",
                    name = name,
                    itemKey = ItemKey.of(name),
                    categoryId = null,
                    boughtAt = nov1 + it * day + 10 * hour,
                    deletedAt = null,
                    pendingUpload = false,
                )
            },
        )
    }

    // Four Saturdays in a row: a weekly regular, due on Friday evening for Saturday.
    private suspend fun weekly(name: String = "Eggs") = buy(name, -22, -15, -8, -1)

    private suspend fun list(id: String, updatedAt: Long, deletedAt: Long? = null, account: String? = owner()) =
        dbRule.listDao.insertList(
            ShoppingListEntity(
                id = id,
                name = "Name of $id",
                createdAt = 1L,
                updatedAt = updatedAt,
                deletedAt = deletedAt,
                accountId = account,
            ),
        )

    private suspend fun item(name: String, listId: String, createdAt: Long, deletedAt: Long? = null) =
        dbRule.itemDao.insertItem(
            ShoppingItemEntity(
                id = "item-$name-$createdAt",
                listId = listId,
                name = name,
                quantity = "1",
                createdAt = createdAt,
                updatedAt = createdAt,
                deletedAt = deletedAt,
            ),
        )

    private suspend fun followUp(
        isoWeek: String,
        postedAt: Long,
        keys: String = "banana",
        account: String = owner().orEmpty(),
        holdout: Boolean = false,
    ) = dbRule.stateDao.insertFollowUp(ReminderFollowUpEntity(account, isoWeek, postedAt, keys, holdout))

    private fun shown() = shadowOf(manager).activeNotifications

    private fun assertSilent() {
        assertTrue("nothing may be posted", shown().isEmpty())
        verify(exactly = 0) { analytics.track(ofType<AnalyticsEvent.GotThisReminderPosted>()) }
    }

    private fun body(): String =
        shown().single().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    // ---- The post ----

    @Test
    fun `posts once for a regular that is due on the eve of the usual day`() = runTest {
        weekly()
        list("old", updatedAt = 5)
        list("new", updatedAt = 9)
        list("gone", updatedAt = 20, deletedAt = 21)

        assertEquals(ListenableWorker.Result.success(), run())

        assertEquals("Eggs is usually bought around now.", body())
        val intent = shadowOf(shown().single().notification.contentIntent).savedIntent
        assertEquals("new", intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_ID))
        assertEquals("Name of new", intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_NAME))
        assertEquals(listOf(week), remote.claimedWeeks)
        verify(exactly = 1) { analytics.track(AnalyticsEvent.GotThisReminderPosted(1, false)) }
        assertEquals(
            listOf(ReminderFollowUpEntity(user, week, fridayEvening, "egg", false)),
            dbRule.stateDao.followUps(user),
        )
    }

    @Test
    fun `with no list the notification asks to create one`() = runTest {
        weekly()

        run()

        val intent = shadowOf(shown().single().notification.contentIntent).savedIntent
        assertNull(intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_ID))
        assertTrue(intent.getBooleanExtra(ReminderIntentParser.EXTRA_CREATE_LIST, false))
    }

    @Test
    fun `names three items and counts the rest`() = runTest {
        listOf("Tea", "Rice", "Milk", "Eggs", "Bread").forEach { weekly(it) }

        run()

        assertEquals("Bread, Eggs, Milk and 2 more are usually bought around now.", body())
        verify { analytics.track(AnalyticsEvent.GotThisReminderPosted(5, false)) }
        assertEquals("bread\negg\nmilk", dbRule.stateDao.followUps(user).single().itemKeys)
    }

    @Test
    fun `nothing is due, nothing is posted`() = runTest {
        buy("Tea", -1)

        run()

        assertSilent()
        assertEquals(emptyList<String>(), remote.claimedWeeks)
    }

    @Test
    fun `nothing is posted when tomorrow is not the usual day`() = runTest {
        weekly()
        clock.now = nov1 + 7 * day + 18 * hour // Sunday: eggs are overdue, the next shop is Saturday

        run()

        assertSilent()
        assertEquals(emptyList<String>(), remote.claimedWeeks)
    }

    @Test
    fun `with no usual day it posts on the first day something is due`() = runTest {
        weekly()
        // Seven one-off days on other weekdays leave Saturday with under 40% of the days.
        buy("Tea", -2, -3, -4, -5, -6, -9, -10)
        clock.now = nov1 + 5 * day + 18 * hour // Friday

        run()

        assertEquals("Eggs is usually bought around now.", body())
    }

    // ---- A cold process ----

    @Test
    fun `a signed-in person is notified from a process with no activity`() = runTest {
        val tokens = inMemoryTokenManager().apply {
            saveToken("token")
            saveUserId(user)
        }
        val auth = AuthStateManager(tokens)
        assertNull("never initialised, as in a process WorkManager started", auth.dataOwnerId())
        storedOwner = StoredOwner { tokens.getUserId() }
        weekly()

        run()

        assertEquals("Eggs is usually bought around now.", body())
        assertEquals(listOf(week), remote.claimedWeeks)
        verify { analytics.track(AnalyticsEvent.GotThisWeeklySummary(1, 4)) }
    }

    // ---- Claiming the week ----

    @Test
    fun `a second run in the same week does not post`() = runTest {
        weekly()
        run()
        remote.claimResult = false

        run()

        assertEquals(1, shown().size)
        assertEquals(listOf(week, week), remote.claimedWeeks)
        verify(exactly = 1) { analytics.track(ofType<AnalyticsEvent.GotThisReminderPosted>()) }
    }

    @Test
    fun `a guest claims the week on the phone and never calls the server`() = runTest {
        storedOwner = StoredOwner { null }
        weekly()

        run()
        assertEquals("Eggs is usually bought around now.", body())
        assertEquals(week, prefs.guestNotifiedWeek)
        assertEquals(
            listOf(ReminderFollowUpEntity("", week, fridayEvening, "egg", false)),
            dbRule.stateDao.followUps(""),
        )

        notificationsCleared()
        run()

        assertTrue("nothing may be shown", shown().isEmpty())
        verify(exactly = 1) { analytics.track(ofType<AnalyticsEvent.GotThisReminderPosted>()) }
        assertEquals(emptyList<String>(), remote.calls)
    }

    @Test
    fun `an offline claim posts nothing and leaves no follow-up row`() = runTest {
        weekly()
        remoteInUse = object : GotThisRemote by remote {
            override suspend fun claimRunningLowWeek(isoWeek: String): Boolean = throw IOException("offline")
        }

        assertEquals(ListenableWorker.Result.success(), run())

        assertSilent()
        assertEquals(emptyList<ReminderFollowUpEntity>(), dbRule.stateDao.followUps(user))
        verify(exactly = 0) { crashReporter.recordNonFatal(any(), any()) }
    }

    @Test
    fun `a cancelled claim is not swallowed`() = runTest {
        weekly()
        remoteInUse = object : GotThisRemote by remote {
            override suspend fun claimRunningLowWeek(isoWeek: String): Boolean = throw CancellationException("stop")
        }

        try {
            run()
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun `a stored owner without a loaded session stays silent`() = runTest {
        remote.sessionUser = null
        weekly()

        run()

        assertSilent()
        assertEquals(emptyList<String>(), remote.claimedWeeks)
        assertEquals(emptyList<ReminderFollowUpEntity>(), dbRule.stateDao.followUps(user))
    }

    @Test
    fun `a session of another user stays silent`() = runTest {
        remote.sessionUser = holdoutUser
        weekly()

        run()

        assertSilent()
        assertEquals(emptyList<String>(), remote.claimedWeeks)
    }

    @Test
    fun `an owner that cannot be read does nothing at all`() = runTest {
        weekly()
        val failure = IllegalStateException("keystore unavailable")
        storedOwner = StoredOwner { throw failure }

        assertEquals(ListenableWorker.Result.success(), run())

        assertSilent()
        verify(exactly = 0) { analytics.track(ofType<AnalyticsEvent.GotThisWeeklySummary>()) }
        assertNull(prefs.lastSummaryWeek)
        assertEquals(emptyList<String>(), remote.calls)
        verify { crashReporter.recordNonFatal(failure, any()) }
    }

    @Test
    fun `without notification permission nothing is claimed`() = runTest {
        weekly()
        gate.enabled = false

        run()

        assertSilent()
        assertEquals(emptyList<String>(), remote.calls)
    }

    @Test
    fun `a holdout person gets no notification but the week is claimed and logged`() = runTest {
        storedOwner = StoredOwner { holdoutUser }
        remote.sessionUser = holdoutUser
        weekly()

        run()

        assertTrue("nothing may be shown", shown().isEmpty())
        assertEquals(listOf(week), remote.claimedWeeks)
        verify(exactly = 1) { analytics.track(AnalyticsEvent.GotThisReminderPosted(1, true)) }
        val row = dbRule.stateDao.followUps(holdoutUser).single()
        assertEquals(true, row.holdout)
        assertEquals("egg", row.itemKeys)
    }

    @Test
    fun `a holdout person without notification permission logs nothing and claims nothing`() = runTest {
        storedOwner = StoredOwner { holdoutUser }
        remote.sessionUser = holdoutUser
        gate.enabled = false
        weekly()

        run()

        assertSilent()
        assertEquals(emptyList<String>(), remote.claimedWeeks)
        assertEquals(emptyList<ReminderFollowUpEntity>(), dbRule.stateDao.followUps(holdoutUser))
    }

    // ---- Switched off ----

    @Test
    fun `killed or switched off still scores and summarises but does nothing else`() = runTest {
        weekly()
        followUp("2026-W43", fridayEvening - 8 * day)
        prefs.killed = true

        run()

        verify { analytics.track(AnalyticsEvent.GotThisReminderFollowUp(false, false)) }
        verify { analytics.track(AnalyticsEvent.GotThisWeeklySummary(1, 4)) }
        assertSilent()
        assertEquals(emptyList<String>(), remote.calls)

        prefs.killed = false
        prefs.runningLowEnabled = false
        prefs.lastSummaryWeek = null
        run()

        verify(exactly = 2) { analytics.track(ofType<AnalyticsEvent.GotThisWeeklySummary>()) }
        assertSilent()
        assertEquals(emptyList<String>(), remote.calls)
    }

    // ---- Follow-ups ----

    @Test
    fun `each row is scored seven days after its own posting and not before`() = runTest {
        prefs.killed = true
        followUp("2026-W43", fridayEvening - 8 * day)
        followUp("2026-W44", fridayEvening - 5 * day)

        run()
        assertEquals(listOf("2026-W44"), dbRule.stateDao.followUps(user).map { it.isoWeek })
        verify(exactly = 1) { analytics.track(ofType<AnalyticsEvent.GotThisReminderFollowUp>()) }

        clock.now = fridayEvening - 5 * day + 7 * day - 1
        run()
        assertEquals(1, dbRule.stateDao.followUps(user).size)

        clock.now = fridayEvening - 5 * day + 7 * day
        run()
        assertEquals(emptyList<ReminderFollowUpEntity>(), dbRule.stateDao.followUps(user))
        verify(exactly = 2) { analytics.track(ofType<AnalyticsEvent.GotThisReminderFollowUp>()) }
    }

    @Test
    fun `adding a misspelt item of a named group counts as added`() = runTest {
        prefs.killed = true
        val postedAt = fridayEvening - 8 * day
        followUp("2026-W43", postedAt, keys = "apple\nbanana", holdout = true)
        list("l1", updatedAt = 1)
        // Created after the posting and deleted again: it still counts.
        item("bannana", "l1", createdAt = postedAt + hour, deletedAt = postedAt + 2 * hour)

        run()

        verify { analytics.track(AnalyticsEvent.GotThisReminderFollowUp(true, true)) }
    }

    @Test
    fun `items from before the posting, other names, other owners and gone lists do not count`() = runTest {
        prefs.killed = true
        val postedAt = fridayEvening - 8 * day
        followUp("2026-W43", postedAt, keys = "banana")
        list("l1", updatedAt = 1)
        list("other", updatedAt = 1, account = "someone-else")
        list("gone", updatedAt = 1, deletedAt = 2)
        item("banana", "l1", createdAt = postedAt - 1)
        item("cheese", "l1", createdAt = postedAt + hour)
        item("banana", "other", createdAt = postedAt + hour)
        item("banana", "gone", createdAt = postedAt + hour)

        run()

        verify { analytics.track(AnalyticsEvent.GotThisReminderFollowUp(false, false)) }
    }

    @Test
    fun `another person's follow-up row is left alone`() = runTest {
        prefs.killed = true
        followUp("2026-W43", fridayEvening - 8 * day, account = "someone-else")

        run()

        verify(exactly = 0) { analytics.track(ofType<AnalyticsEvent.GotThisReminderFollowUp>()) }
        assertEquals(1, dbRule.stateDao.followUps("someone-else").size)
    }

    // ---- The weekly summary ----

    @Test
    fun `the summary goes out once per ISO week`() = runTest {
        weekly()

        run()
        run()

        verify(exactly = 1) { analytics.track(AnalyticsEvent.GotThisWeeklySummary(1, 4)) }
        assertEquals(week, prefs.lastSummaryWeek)

        clock.now += 7 * day
        run()

        verify(exactly = 2) { analytics.track(ofType<AnalyticsEvent.GotThisWeeklySummary>()) }
        assertEquals("2026-W46", prefs.lastSummaryWeek)
    }

    // ---- Failure ----

    @Test
    fun `an exception is recorded and the result is still success`() = runTest {
        weekly()
        val failure = IllegalStateException("boom")
        notificationGate = NotificationGate { throw failure }

        assertEquals(ListenableWorker.Result.success(), run())

        verify { crashReporter.recordNonFatal(failure, any()) }
        assertSilent()
    }

    @Test
    fun `a cancellation is rethrown, not recorded`() = runTest {
        weekly()
        notificationGate = NotificationGate { throw CancellationException("stop") }

        try {
            run()
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
        }

        verify(exactly = 0) { crashReporter.recordNonFatal(any(), any()) }
    }

    private fun notificationsCleared() = manager.cancelAll()
}
