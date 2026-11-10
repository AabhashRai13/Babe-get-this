package com.babegetthis.android.feature.gotthis.data

import com.babegetthis.android.core.sync.data.mapper.isoToMillis
import com.babegetthis.android.feature.gotthis.data.local.model.HintImpressionEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ReminderFollowUpEntity
import com.babegetthis.android.feature.gotthis.data.local.model.TickOutboxEntity
import com.babegetthis.android.feature.gotthis.data.remote.PurchaseRow
import com.babegetthis.android.feature.gotthis.domain.GotThisIds
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.FakeGotThisRemote
import com.babegetthis.android.testing.FakeSyncPointStore
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.gotThisSyncFor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GotThisSyncPullTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val remote = FakeGotThisRemote()
    private val prefs = FakeGotThisPrefs()
    private val syncPoints = FakeSyncPointStore()
    private val sync by lazy { gotThisSyncFor(dbRule, remote, prefs, syncPoints) }
    private val dao get() = dbRule.purchaseDao
    private val state get() = dbRule.stateDao

    private val t09 = "2026-11-01T09:00:00Z"
    private val t10 = "2026-11-01T10:00:00Z"
    private val t11 = "2026-11-01T11:00:00Z"
    private val t12 = "2026-11-01T12:00:00Z"

    private fun row(
        tickId: String,
        boughtAt: String,
        updatedAt: String = boughtAt,
        name: String = "Whole Milk",
        deletedAt: String? = null,
    ) = PurchaseRow(
        id = "p-$tickId",
        userId = "u1",
        tickId = tickId,
        sourceItemId = "item-$tickId",
        name = name,
        categoryId = "cat-dairy-eggs",
        boughtAt = boughtAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun local(tickId: String, boughtAt: String, deletedAt: Long? = null) = PurchaseEntity(
        id = "p-$tickId",
        tickId = tickId,
        accountId = "u1",
        sourceItemId = "item-$tickId",
        name = "Milk",
        itemKey = "milk",
        categoryId = null,
        boughtAt = boughtAt.isoToMillis(),
        deletedAt = deletedAt,
        pendingUpload = false,
    )

    private suspend fun seedState() {
        state.upsertMute(ItemMuteEntity("u1", "milk", 2, 5L))
        state.upsertMute(ItemMuteEntity("", "milk", 2, 5L))
        state.insertImpressionIfAbsent(HintImpressionEntity("i1", "u1", "milk", 1L, false))
        state.insertFollowUp(ReminderFollowUpEntity("u1", "2026-W45", 1L, "milk", false))
    }

    private suspend fun ids() = dao.live("u1").map { it.tickId }.sorted()

    @Test
    fun `rows are upserted with a locally computed key and the point moves to the newest update`() = runTest {
        remote.purchaseRows = listOf(
            row("t1", t10, updatedAt = t10),
            row("t2", t11, updatedAt = t12, name = "2 Organic Bananas"),
        )

        sync.pull("u1")

        val t2 = dao.byTickId("t2").single()
        assertEquals("banana", t2.itemKey)
        assertEquals("u1", t2.accountId)
        assertEquals(false, t2.pendingUpload)
        assertEquals(t11.isoToMillis(), t2.boughtAt)
        assertEquals(listOf("t1", "t2"), ids())
        assertEquals(t12, syncPoints.get("purchases:u1"))
        assertEquals(listOf(null), remote.fetchSince)
    }

    @Test
    fun `a tombstone replaces a live row`() = runTest {
        dao.upsert(listOf(local("t1", t10)))
        remote.purchaseRows = listOf(row("t1", t10, updatedAt = t11, deletedAt = t11))

        sync.pull("u1")

        assertEquals(t11.isoToMillis(), dao.byTickId("t1").single().deletedAt)
        assertTrue(ids().isEmpty())
    }

    @Test
    fun `a failed fetch moves nothing`() = runTest {
        syncPoints.set("purchases:u1", t09)
        remote.failFetch = true

        runCatching { sync.pull("u1") }

        assertEquals(t09, syncPoints.get("purchases:u1"))
        assertNull(dao.live("u1").firstOrNull())
    }

    @Test
    fun `another account's sync point is not used`() = runTest {
        syncPoints.set("purchases:u2", t12)

        sync.pull("u1")

        assertEquals(listOf<String?>(null), remote.fetchSince)
    }

    @Test
    fun `no rows leaves the point where it was`() = runTest {
        syncPoints.set("purchases:u1", t09)

        sync.pull("u1")

        assertEquals(t09, syncPoints.get("purchases:u1"))
        assertEquals(listOf(t09), remote.fetchSince)
    }

    @Test
    fun `a newer server reset deletes up to it, keeps the outbox and clears state and the point`() = runTest {
        dao.upsert(listOf(local("old", t09), local("edge", t10), local("late", t12)))
        dao.insertOutbox(TickOutboxEntity("o1", "u1", "i", "l", t09.isoToMillis(), "Milk", null, null, 0))
        seedState()
        syncPoints.set("purchases:u1", t09)
        remote.resetAt = t10

        sync.pull("u1")

        assertEquals(listOf("late"), ids())
        assertNotNull(dao.outboxByTickId("o1"))
        assertTrue(state.mutes("u1").isEmpty())
        assertEquals(1, state.mutes("").size)
        assertNull(state.impression("i1"))
        assertTrue(state.followUps("u1").isEmpty())
        assertEquals(t10.isoToMillis(), prefs.lastAppliedReset("u1"))
        // Cleared before the fetch, so the fetch started from the beginning.
        assertEquals(listOf(null), remote.fetchSince)
    }

    @Test
    fun `an older or equal reset changes nothing`() = runTest {
        prefs.setLastAppliedReset("u1", t10.isoToMillis())
        dao.upsert(listOf(local("t1", t09), local("t2", t11)))
        seedState()
        syncPoints.set("purchases:u1", t09)
        remote.resetAt = t10

        sync.pull("u1")
        remote.resetAt = t09
        sync.pull("u1")

        assertEquals(listOf("t1", "t2"), ids())
        assertEquals(1, state.mutes("u1").size)
        assertEquals(listOf(t09, t09), remote.fetchSince)
    }

    @Test
    fun `a pending clear keeps older purchases out even when the server has no reset`() = runTest {
        prefs.setPendingClear("u1", t10.isoToMillis())
        remote.purchaseRows = listOf(row("t09", t09), row("t11", t11))

        sync.pull("u1")

        assertEquals(listOf("t11"), ids())
        assertTrue(dao.byTickId("t09").isEmpty())
        // Discarded rows still count for the point.
        assertEquals(t11, syncPoints.get("purchases:u1"))
    }

    @Test
    fun `after the clear is acknowledged a pull still discards older purchases`() = runTest {
        prefs.setPendingClear("u1", t10.isoToMillis())
        sync.push("u1")
        assertNull(prefs.pendingClear("u1"))
        remote.purchaseRows = listOf(row("t09", t09), row("t11", t11))

        sync.pull("u1")

        assertEquals(listOf("t11"), ids())
    }

    @Test
    fun `a clear pressed while the fetch is in flight keeps the fetched older rows out`() = runTest {
        remote.purchaseRows = listOf(row("t09", t09), row("t10", t10), row("t12", t12))
        remote.onFetch = {
            // What GotThisRepository.clearHistory does at t11.
            prefs.setPendingClear("u1", t11.isoToMillis())
            dao.deleteUpTo("u1", t11.isoToMillis())
        }

        sync.pull("u1")

        assertTrue(dao.all().none { it.boughtAt <= t11.isoToMillis() })
        assertEquals(listOf("t12"), ids())
    }

    @Test
    fun `only discarded rows still move the point`() = runTest {
        prefs.setLastAppliedReset("u1", t11.isoToMillis())
        remote.purchaseRows = listOf(row("t09", t09, updatedAt = t12))

        sync.pull("u1")

        assertTrue(dao.byTickId("t09").isEmpty())
        assertEquals(t12, syncPoints.get("purchases:u1"))
    }

    @Test
    fun `a live server row does not undo an undo still in the outbox`() = runTest {
        dao.upsert(listOf(local("t1", t10, deletedAt = t11.isoToMillis())))
        dao.insertOutbox(
            TickOutboxEntity("t1", "u1", "i", "l", t10.isoToMillis(), "Milk", null, t11.isoToMillis(), 1),
        )
        dao.insertOutbox(TickOutboxEntity("t2", "u1", "i", "l", t10.isoToMillis(), "Milk", null, null, 0))
        remote.purchaseRows = listOf(row("t1", t10, updatedAt = t12), row("t2", t10, updatedAt = t12))

        sync.pull("u1")

        assertEquals(t11.isoToMillis(), dao.byTickId("t1").single().deletedAt)
        assertEquals(listOf("t2"), ids())
        assertEquals(t12, syncPoints.get("purchases:u1"))
    }

    @Test
    fun `the outbox is checked in chunks within the SQL variable limit`() = runTest {
        remote.purchaseRows = (1..33_000).map { row("t$it", t10, updatedAt = t11) }
        // More ids than SQLite's variable limit (32766 on current builds, 999 on old ones): one IN list would throw.
        sync.pull("u1")

        assertEquals(33_000, ids().size)
    }

    // ── Sign-in and sign-out ────────────────────────────────────────────────

    @Test
    fun `claiming turns guest purchases into the account's and flags them for upload`() = runTest {
        dao.upsert(
            listOf(
                local("t1", t10).copy(id = GotThisIds.guestPurchaseId("t1"), accountId = null),
                local("t2", t10).copy(id = GotThisIds.guestPurchaseId("t2"), accountId = null, deletedAt = 5L),
            ),
        )
        state.upsertMute(ItemMuteEntity("", "milk", 2, 5L))

        sync.claimGuestHistory("u1")

        val claimed = dao.byTickId("t1").single()
        assertEquals(GotThisIds.purchaseId("u1", "t1"), claimed.id)
        assertEquals("u1", claimed.accountId)
        assertTrue(claimed.pendingUpload)
        val tombstone = dao.byTickId("t2").single()
        assertEquals("u1", tombstone.accountId)
        assertEquals(false, tombstone.pendingUpload)
        assertTrue(dao.guestPurchases().isEmpty())
        assertEquals(listOf("milk"), state.mutes("u1").map { it.itemKey })
        assertTrue(state.mutes("").isEmpty())
    }

    @Test
    fun `evict removes the account's history and keeps the guest's and the pending clear`() = runTest {
        dao.upsert(listOf(local("t1", t10), local("g1", t10).copy(id = "g", accountId = null)))
        dao.insertOutbox(TickOutboxEntity("o1", "u1", "i", "l", 1L, "Milk", null, null, 0))
        seedState()
        prefs.setPendingClear("u1", t10.isoToMillis())

        sync.evict()

        assertTrue(dao.live("u1").isEmpty())
        assertEquals(1, dao.guestPurchases().size)
        assertNull(dao.outboxByTickId("o1"))
        assertTrue(state.mutes("u1").isEmpty())
        assertEquals(1, state.mutes("").size)
        assertNull(state.impression("i1"))
        assertTrue(state.followUps("u1").isEmpty())
        assertEquals(t10.isoToMillis(), prefs.pendingClear("u1"))
    }
}
