package com.babegetthis.android.feature.gotthis.data

import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.sync.data.mapper.millisToIso
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.TickOutboxEntity
import com.babegetthis.android.feature.gotthis.data.remote.TickReply
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.FakeGotThisRemote
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.gotThisSyncFor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GotThisSyncPushTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_793_527_200_000L
    private val remote = FakeGotThisRemote()
    private val prefs = FakeGotThisPrefs()
    private val sync by lazy { gotThisSyncFor(dbRule, remote, prefs, clock = ReminderClock { now }) }
    private val dao get() = dbRule.purchaseDao

    private suspend fun outbox(
        tickId: String,
        accountId: String = "u1",
        listId: String = "list-1",
        tickedAt: Long = now,
        undoneAt: Long? = null,
        revision: Int = 0,
    ) = dao.insertOutbox(
        TickOutboxEntity(tickId, accountId, "item-$tickId", listId, tickedAt, "Milk", "dairy-eggs", undoneAt, revision),
    )

    private suspend fun purchase(
        tickId: String,
        accountId: String? = "u1",
        pendingUpload: Boolean = false,
        deletedAt: Long? = null,
    ) = dao.upsert(
        listOf(
            PurchaseEntity(
                id = "p-$tickId",
                tickId = tickId,
                accountId = accountId,
                sourceItemId = "item-$tickId",
                name = "Milk",
                itemKey = "milk",
                categoryId = null,
                boughtAt = now,
                deletedAt = deletedAt,
                pendingUpload = pendingUpload,
            ),
        ),
    )

    private suspend fun list(id: String, onServer: Boolean, deletedAt: Long?) = dbRule.listDao.insertList(
        ShoppingListEntity(
            id = id,
            name = "L",
            createdAt = 1L,
            updatedAt = 2L,
            accountId = "u1",
            onServer = onServer,
            deletedAt = deletedAt,
        ),
    )

    @Test
    fun `clear, events and own purchases go in that order`() = runTest {
        prefs.setPendingClear("u1", now - day)
        outbox("t1")
        purchase("t2", pendingUpload = true)

        sync.push("u1")

        assertEquals(listOf("clearHistory", "recordTicks", "uploadOwnPurchases"), remote.calls)
    }

    @Test
    fun `a clear that succeeds stores the returned reset and is no longer pending`() = runTest {
        prefs.setPendingClear("u1", now - day)

        sync.push("u1")

        assertEquals(listOf((now - day).millisToIso()), remote.clearedAt)
        assertEquals(now - day, prefs.lastAppliedReset("u1"))
        assertNull(prefs.pendingClear("u1"))
    }

    @Test
    fun `a clear that fails stays pending and the events are still sent`() = runTest {
        prefs.setPendingClear("u1", now - day)
        remote.failClear = true
        outbox("t1")

        sync.push("u1")

        assertEquals(now - day, prefs.pendingClear("u1"))
        assertNull(prefs.lastAppliedReset("u1"))
        assertEquals(listOf("clearHistory", "recordTicks"), remote.calls)
        assertEquals(0, dao.countOutbox("u1"))
    }

    @Test
    fun `a clear pressed during the clear call stays pending and is sent next`() = runTest {
        prefs.setPendingClear("u1", now - day)
        remote.onClear = { prefs.setPendingClear("u1", now) }

        sync.push("u1")

        assertEquals(now, prefs.pendingClear("u1"))
        assertEquals(now - day, prefs.lastAppliedReset("u1"))
        remote.onClear = {}
        sync.push("u1")
        assertEquals(listOf((now - day).millisToIso(), now.millisToIso()), remote.clearedAt)
        assertNull(prefs.pendingClear("u1"))
    }

    @Test
    fun `cancellation during the clear is not swallowed`() = runTest {
        prefs.setPendingClear("u1", now - day)
        remote.clearFailure = kotlinx.coroutines.CancellationException("cancelled")

        try {
            sync.push("u1")
            org.junit.Assert.fail("expected cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) {
        }
        assertEquals(now - day, prefs.pendingClear("u1"))
    }

    @Test
    fun `1200 outbox rows go in batches of 500, 500 and 200`() = runTest {
        repeat(1200) { outbox("t%04d".format(it), tickedAt = now + it) }

        sync.push("u1")

        assertEquals(listOf(500, 500, 200), remote.recordedBatches.map { it.size })
        assertEquals(0, dao.countOutbox("u1"))
    }

    @Test
    fun `accepted and rejected rows are deleted and retry rows stay with the same revision`() = runTest {
        outbox("ok")
        outbox("bad")
        outbox("later", revision = 3)
        remote.reply = { TickReply(listOf("ok"), listOf("later"), listOf("bad")) }

        sync.push("u1")

        assertNull(dao.outboxByTickId("ok"))
        assertNull(dao.outboxByTickId("bad"))
        assertEquals(3, dao.outboxByTickId("later")?.revision)
        // Retry rows are not re-sent within the same push.
        assertEquals(1, remote.calls.count { it == "recordTicks" })
    }

    @Test
    fun `retry rows at the front do not hide the rows behind them`() = runTest {
        repeat(500) { outbox("a%04d".format(it), tickedAt = now + it) }
        outbox("z", tickedAt = now + 1000)
        remote.reply = { events ->
            val (done, later) = events.map { it.id }.partition { it == "z" }
            TickReply(done, later, emptyList())
        }

        sync.push("u1")

        assertNull(dao.outboxByTickId("z"))
        assertEquals(500, dao.countOutbox("u1"))
        assertEquals(2, remote.calls.count { it == "recordTicks" })
    }

    @Test
    fun `an undo applied during the call keeps the row for the next push`() = runTest {
        outbox("t1")
        purchase("t1")
        remote.onRecordTicks = { dao.insertOutbox(dao.outboxByTickId("t1")!!.copy(undoneAt = now + 1, revision = 1)) }

        sync.push("u1")

        assertEquals(1, dao.outboxByTickId("t1")?.revision)
        remote.onRecordTicks = {}
        sync.push("u1")
        assertNull(dao.outboxByTickId("t1"))
        assertEquals((now + 1).millisToIso(), remote.recordedBatches.last().single().undoneAt)
    }

    @Test
    fun `a rejected event more than 30 days ahead drops its purchase, other rejections keep it`() = runTest {
        outbox("far", tickedAt = now + 31 * day)
        outbox("near", tickedAt = now + 10 * day)
        purchase("far")
        purchase("near")
        remote.reply = { TickReply(emptyList(), emptyList(), it.map { e -> e.id }) }

        sync.push("u1")

        assertTrue(dao.byTickId("far").isEmpty())
        assertEquals(1, dao.byTickId("near").size)
        assertEquals(0, dao.countOutbox("u1"))
    }

    @Test
    fun `a far-future rejection keeps its purchase when an undo changed the row mid-call`() = runTest {
        outbox("far", tickedAt = now + 31 * day)
        purchase("far")
        remote.onRecordTicks =
            { dao.insertOutbox(dao.outboxByTickId("far")!!.copy(undoneAt = now + 31 * day, revision = 1)) }
        remote.reply = { TickReply(emptyList(), emptyList(), it.map { e -> e.id }) }

        sync.push("u1")

        assertEquals(1, dao.byTickId("far").size)
        assertEquals(1, dao.countOutbox("u1"))
    }

    @Test
    fun `a failed record call propagates and leaves the rows`() = runTest {
        outbox("t1")
        remote.failRecord = true

        try {
            sync.push("u1")
            org.junit.Assert.fail("expected failure")
        } catch (_: java.io.IOException) {
        }
        assertEquals(1, dao.countOutbox("u1"))
    }

    @Test
    fun `own purchases are marked uploaded on success`() = runTest {
        purchase("t1", pendingUpload = true)

        sync.push("u1")

        assertEquals(1, remote.uploadedBatches.single().size)
        assertEquals("t1", remote.uploadedBatches.single().single().tickId)
        assertEquals(0, dao.countPendingUploads("u1"))
    }

    @Test
    fun `a failed upload leaves purchases pending`() = runTest {
        purchase("t1", pendingUpload = true)
        remote.failUpload = true

        try {
            sync.push("u1")
            org.junit.Assert.fail("expected failure")
        } catch (_: java.io.IOException) {
        }
        assertEquals(1, dao.countPendingUploads("u1"))
    }

    @Test
    fun `only the given account's outbox rows are sent`() = runTest {
        outbox("mine")
        outbox("theirs", accountId = "u2")

        sync.push("u1")

        assertEquals(listOf("mine"), remote.recordedBatches.flatten().map { it.id })
        assertNotNull(dao.outboxByTickId("theirs"))
    }

    @Test
    fun `dropping never-sent lists makes live ticks uploads and clears the outbox`() = runTest {
        list("gone", onServer = false, deletedAt = 5L)
        list("kept", onServer = true, deletedAt = 5L)
        outbox("live", listId = "gone")
        outbox("undone", listId = "gone", undoneAt = now + 1)
        outbox("onserver", listId = "kept")
        purchase("live")
        purchase("undone", deletedAt = now + 1)
        purchase("onserver")

        sync.dropNeverSentLists()

        assertTrue(dao.byTickId("live").single().pendingUpload)
        assertFalse(dao.byTickId("undone").single().pendingUpload)
        assertFalse(dao.byTickId("onserver").single().pendingUpload)
        assertNull(dao.outboxByTickId("live"))
        assertNull(dao.outboxByTickId("undone"))
        assertNotNull(dao.outboxByTickId("onserver"))
        assertNull(dbRule.listDao.getListRaw("gone"))
        assertNotNull(dbRule.listDao.getListRaw("kept"))
    }

    @Test
    fun `unsynced count is 1 for each kind of waiting work alone`() = runTest {
        assertEquals(0, sync.unsyncedCount("u1"))

        outbox("t1")
        assertEquals(1, sync.unsyncedCount("u1"))
        dao.deleteOutboxFor("u1")

        purchase("t2", pendingUpload = true)
        assertEquals(1, sync.unsyncedCount("u1"))
        dao.markUploaded(listOf("p-t2"))

        prefs.setPendingClear("u1", now)
        assertEquals(1, sync.unsyncedCount("u1"))
    }
}
