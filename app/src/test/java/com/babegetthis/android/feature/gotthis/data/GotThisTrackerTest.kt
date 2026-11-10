package com.babegetthis.android.feature.gotthis.data

import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.HintOutcome
import com.babegetthis.android.feature.gotthis.data.local.dao.GotThisStateDao
import com.babegetthis.android.feature.gotthis.data.local.model.HintImpressionEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.domain.HintSource
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.InMemoryDatabaseRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class GotThisTrackerTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val day = 86_400_000L

    // 2026-11-01 12:00 UTC
    private var now = 1_793_534_400_000L
    private val prefs = FakeGotThisPrefs()
    private val access = GotThisAccess(FeatureFlagCache(), prefs)
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : AnalyticsRepository {
        override fun track(event: AnalyticsEvent) {
            events += event
        }

        override fun setUser(userId: String?) = Unit

        override fun setCollectionEnabled(enabled: Boolean) = Unit
    }
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val tracker by lazy {
        GotThisTracker(dbRule.db, analytics, access, prefs, ReminderClock { now }, crashReporter) {
            TimeZone.getTimeZone("UTC")
        }
    }

    // holdout number 12 / 2
    private val regular = "22222222-2222-2222-2222-222222222222"
    private val heldOut = "11111111-1111-1111-1111-111111111111"

    private fun hint(itemId: String, key: String = "milk", daysAgo: Int = 2, source: HintSource = HintSource.Regular) =
        BoughtHint(itemId, daysAgo, source, key)

    private suspend fun impression(
        itemId: String,
        account: String = regular,
        key: String = "milk",
        holdout: Boolean = false,
    ) = dbRule.stateDao.insertImpressionIfAbsent(HintImpressionEntity(itemId, account, key, now, holdout))

    @Test
    fun `hints present twice for one item give one event and one row`() = runTest {
        tracker.onHintsPresent(regular, listOf(hint("i1")))
        tracker.onHintsPresent(regular, listOf(hint("i1")))

        assertEquals(listOf(AnalyticsEvent.GotThisHintShown(HintSource.Regular, 2, holdout = false)), events)
        val row = dbRule.stateDao.impression("i1")!!
        assertEquals(HintImpressionEntity("i1", regular, "milk", now, false), row)
    }

    @Test
    fun `hints for several items each get an impression`() = runTest {
        tracker.onHintsPresent(regular, listOf(hint("i1"), hint("i2", "eggs", 5, HintSource.Category)))

        assertEquals(
            listOf(
                AnalyticsEvent.GotThisHintShown(HintSource.Regular, 2, false),
                AnalyticsEvent.GotThisHintShown(HintSource.Category, 5, false),
            ),
            events,
        )
    }

    @Test
    fun `hints in the holdout are logged with holdout true`() = runTest {
        tracker.onHintsPresent(heldOut, listOf(hint("i1")))

        assertEquals(listOf(AnalyticsEvent.GotThisHintShown(HintSource.Regular, 2, holdout = true)), events)
        assertTrue(dbRule.stateDao.impression("i1")!!.holdout)
    }

    @Test
    fun `guest hints use an empty account id`() = runTest {
        tracker.onHintsPresent(null, listOf(hint("i1")))

        assertEquals("", dbRule.stateDao.impression("i1")!!.accountId)
        assertEquals(1, events.size)
    }

    @Test
    fun `hints do nothing when the feature is killed`() = runTest {
        prefs.killed = true

        tracker.onHintsPresent(regular, listOf(hint("i1")))

        assertTrue(events.isEmpty())
        assertNull(dbRule.stateDao.impression("i1"))
    }

    @Test
    fun `hints do nothing when the person turned hints off`() = runTest {
        prefs.hintsEnabled = false

        tracker.onHintsPresent(regular, listOf(hint("i1")))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `with hints switched off a holdout person logs no hint shown`() = runTest {
        prefs.hintsEnabled = false

        tracker.onHintsPresent(heldOut, listOf(hint("i1")))

        assertTrue(events.isEmpty())
        assertNull(dbRule.stateDao.impression("i1"))
    }

    @Test
    fun `a tick with an impression reports ticked and removes the row`() = runTest {
        impression("i1")

        tracker.onItemTicked("i1", null)

        assertEquals(listOf(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Ticked, holdout = false)), events)
        assertNull(dbRule.stateDao.impression("i1"))
        assertEquals(1, dbRule.stateDao.mutes(regular).single().keptInARow)
    }

    @Test
    fun `a tick without an impression reports no outcome`() = runTest {
        tracker.onItemTicked("i1", null)

        assertTrue(events.isEmpty())
        assertTrue(dbRule.stateDao.mutes(regular).isEmpty())
    }

    @Test
    fun `two kept in a row mute the item for 60 days and reset the counter`() = runTest {
        impression("i1")
        tracker.onItemTicked("i1", null)
        impression("i2")
        tracker.onItemTicked("i2", null)

        assertEquals(
            ItemMuteEntity(regular, "milk", keptInARow = 0, mutedUntil = now + 60 * day),
            dbRule.stateDao.mutes(regular).single(),
        )
        assertEquals(2, events.size)
    }

    @Test
    fun `a holdout person gets the mute after two kept, as outside the holdout`() = runTest {
        impression("i1", account = heldOut, holdout = true)
        tracker.onItemTicked("i1", null)
        impression("i2", account = heldOut, holdout = true)
        tracker.onItemTicked("i2", null)

        assertEquals(
            listOf(
                AnalyticsEvent.GotThisHintOutcome(HintOutcome.Ticked, holdout = true),
                AnalyticsEvent.GotThisHintOutcome(HintOutcome.Ticked, holdout = true),
            ),
            events,
        )
        assertEquals(
            ItemMuteEntity(heldOut, "milk", keptInARow = 0, mutedUntil = now + 60 * day),
            dbRule.stateDao.mutes(heldOut).single(),
        )
    }

    @Test
    fun `a delete reports removed, drops the row and resets the counter`() = runTest {
        dbRule.stateDao.upsertMute(ItemMuteEntity(regular, "milk", keptInARow = 1, mutedUntil = null))
        impression("i1")

        tracker.onItemDeleted("i1")

        assertEquals(listOf(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Removed, holdout = false)), events)
        assertNull(dbRule.stateDao.impression("i1"))
        assertEquals(0, dbRule.stateDao.mutes(regular).single().keptInARow)
    }

    @Test
    fun `a delete with no mute row creates none`() = runTest {
        impression("i1")

        tracker.onItemDeleted("i1")

        assertTrue(dbRule.stateDao.mutes(regular).isEmpty())
    }

    @Test
    fun `a delete in the holdout resets the counter too`() = runTest {
        dbRule.stateDao.upsertMute(ItemMuteEntity(heldOut, "milk", keptInARow = 1, mutedUntil = null))
        impression("i1", account = heldOut, holdout = true)

        tracker.onItemDeleted("i1")

        assertEquals(listOf(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Removed, holdout = true)), events)
        assertEquals(0, dbRule.stateDao.mutes(heldOut).single().keptInARow)
    }

    @Test
    fun `a delete without an impression does nothing`() = runTest {
        tracker.onItemDeleted("i1")

        assertTrue(events.isEmpty())
    }

    @Test
    fun `muting reports muted and mutes for 60 days`() = runTest {
        dbRule.stateDao.upsertMute(ItemMuteEntity(regular, "milk", keptInARow = 1, mutedUntil = null))
        impression("i1")

        tracker.onHintMuted("i1")

        assertEquals(listOf(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Muted, holdout = false)), events)
        assertNull(dbRule.stateDao.impression("i1"))
        assertEquals(
            ItemMuteEntity(regular, "milk", keptInARow = 0, mutedUntil = now + 60 * day),
            dbRule.stateDao.mutes(regular).single(),
        )
    }

    @Test
    fun `muting without an impression does nothing`() = runTest {
        tracker.onHintMuted("i1")

        assertTrue(events.isEmpty())
        assertTrue(dbRule.stateDao.mutes(regular).isEmpty())
    }

    private suspend fun buy(account: String?, key: String, vararg daysAgo: Int) {
        dbRule.purchaseDao.upsert(
            daysAgo.mapIndexed { index, ago ->
                PurchaseEntity(
                    id = "p-$key-$ago-$index",
                    tickId = "t-$key-$ago-$index",
                    accountId = account,
                    sourceItemId = "i",
                    name = key,
                    itemKey = key,
                    categoryId = null,
                    boughtAt = now - ago * day,
                    deletedAt = null,
                    pendingUpload = false,
                )
            },
        )
    }

    private fun tick(owner: String?, key: String = "milk", recorded: Boolean = true) =
        TickResult("list-1", owner != null, owner, key, undone = false, recorded = recorded)

    @Test
    fun `a tick that starts a new shop of a weekly regular scores zero`() = runTest {
        buy(regular, "milk", 28, 21, 14, 7, 0)

        tracker.onItemTicked("i1", tick(regular))

        assertEquals(listOf(AnalyticsEvent.GotThisPredictionScored(0)), events)
    }

    @Test
    fun `a late purchase scores positive`() = runTest {
        buy(regular, "milk", 31, 24, 17, 10, 0)

        tracker.onItemTicked("i1", tick(regular))

        assertEquals(listOf(AnalyticsEvent.GotThisPredictionScored(3)), events)
    }

    @Test
    fun `typo variants in the group are scored together`() = runTest {
        buy(regular, "yoghurt", 28, 21, 14, 0)
        buy(regular, "yogurt", 7)

        tracker.onItemTicked("i1", tick(regular, key = "yogurt"))

        assertEquals(listOf(AnalyticsEvent.GotThisPredictionScored(0)), events)
    }

    @Test
    fun `a guest tick is scored from the guest history`() = runTest {
        buy(null, "milk", 28, 21, 14, 7, 0)

        tracker.onItemTicked("i1", tick(null))

        assertEquals(listOf(AnalyticsEvent.GotThisPredictionScored(0)), events)
    }

    @Test
    fun `purchases dated after now are not scored`() = runTest {
        buy(regular, "milk", 28, 21, 14, 7, 0, -3)

        tracker.onItemTicked("i1", tick(regular))

        assertEquals(listOf(AnalyticsEvent.GotThisPredictionScored(0)), events)
    }

    @Test
    fun `an item that was not a regular is not scored`() = runTest {
        buy(regular, "milk", 7, 0)

        tracker.onItemTicked("i1", tick(regular))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a tick that wrote nothing is not scored`() = runTest {
        buy(regular, "milk", 28, 21, 14, 7, 0)

        tracker.onItemTicked("i1", tick(regular, recorded = false))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a tick whose group is unknown is not scored`() = runTest {
        buy(regular, "milk", 28, 21, 14, 7, 0)

        tracker.onItemTicked("i1", tick(regular, key = "bread"))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `usuals shown twice on one day give one event`() = runTest {
        tracker.onUsualsPresent(regular, 3)
        tracker.onUsualsPresent(regular, 4)

        assertEquals(listOf(AnalyticsEvent.GotThisUsualsShown(3, holdout = false)), events)
    }

    @Test
    fun `usuals shown again the next day give a second event`() = runTest {
        tracker.onUsualsPresent(regular, 3)
        now += day
        tracker.onUsualsPresent(regular, 3)

        assertEquals(2, events.size)
    }

    @Test
    fun `usuals in the holdout are logged with holdout true`() = runTest {
        tracker.onUsualsPresent(heldOut, 2)

        assertEquals(listOf(AnalyticsEvent.GotThisUsualsShown(2, holdout = true)), events)
    }

    @Test
    fun `with running low switched off a holdout person logs no usuals shown`() = runTest {
        prefs.runningLowEnabled = false

        tracker.onUsualsPresent(heldOut, 2)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `usuals do nothing when running low is off`() = runTest {
        prefs.runningLowEnabled = false

        tracker.onUsualsPresent(regular, 3)

        assertTrue(events.isEmpty())
        assertEquals(0L, prefs.lastUsualsShownDay)
    }

    @Test
    fun `no event carries an item name or key`() = runTest {
        impression("i1", key = "secretkey")
        buy(regular, "secretkey", 28, 21, 14, 7, 0)
        tracker.onHintsPresent(regular, listOf(hint("i2", "secretkey")))
        tracker.onItemTicked("i1", tick(regular, key = "secretkey"))

        assertNotNull(events.firstOrNull())
        events.forEach { assertTrue(!it.toString().contains("secretkey")) }
    }

    @Test
    fun `a failing database never escapes any method and each failure is reported once`() = runTest {
        val stateDao = mockk<GotThisStateDao>()
        coEvery { stateDao.insertImpressionIfAbsent(any()) } throws IllegalStateException("boom")
        coEvery { stateDao.impression(any()) } throws IllegalStateException("boom")
        val db = mockk<AppDatabase> {
            every { gotThisStateDao() } returns stateDao
            every { purchaseDao() } returns dbRule.purchaseDao
        }
        val t = GotThisTracker(db, analytics, access, prefs, ReminderClock { now }, crashReporter) {
            TimeZone.getTimeZone("UTC")
        }

        t.onHintsPresent(regular, listOf(hint("i1")))
        t.onItemTicked("i1", tick(regular))
        t.onItemDeleted("i1")
        t.onHintMuted("i1")

        verify(exactly = 4) { crashReporter.recordNonFatal(any<IllegalStateException>(), any()) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a failing usuals write is reported and swallowed`() = runTest {
        val failing = spyk(FakeGotThisPrefs())
        every { failing.lastUsualsShownDay = any() } throws IllegalStateException("boom")
        val t =
            GotThisTracker(
                dbRule.db,
                analytics,
                GotThisAccess(FeatureFlagCache(), failing),
                failing,
                ReminderClock {
                    now
                },
                crashReporter,
            ) {
                TimeZone.getTimeZone("UTC")
            }

        t.onUsualsPresent(regular, 3)

        verify(exactly = 1) { crashReporter.recordNonFatal(any(), any()) }
    }

    @Test
    fun `cancellation is rethrown and not reported`() = runTest {
        val failing = spyk(FakeGotThisPrefs())
        every { failing.lastUsualsShownDay = any() } throws CancellationException("cancelled")
        val t =
            GotThisTracker(
                dbRule.db,
                analytics,
                GotThisAccess(FeatureFlagCache(), failing),
                failing,
                ReminderClock {
                    now
                },
                crashReporter,
            ) {
                TimeZone.getTimeZone("UTC")
            }

        try {
            t.onUsualsPresent(regular, 3)
            fail("expected cancellation")
        } catch (_: CancellationException) {
        }

        verify(exactly = 0) { crashReporter.recordNonFatal(any(), any()) }
    }
}
