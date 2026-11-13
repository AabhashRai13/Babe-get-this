package com.babegetthis.android.feature.gotthis.data.local

import com.babegetthis.android.feature.gotthis.data.local.dao.KeyStatRow
import com.babegetthis.android.feature.gotthis.data.local.model.HintImpressionEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ReminderFollowUpEntity
import com.babegetthis.android.feature.gotthis.data.local.model.TickOutboxEntity
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.TestData
import kotlinx.coroutines.flow.first
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
class PurchaseDaoTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val purchases get() = dbRule.purchaseDao
    private val state get() = dbRule.stateDao

    private fun purchase(
        id: String,
        accountId: String? = "u1",
        itemKey: String = "milk",
        boughtAt: Long = 1_000L,
        tickId: String = "t-$id",
        deletedAt: Long? = null,
        pendingUpload: Boolean = false,
    ) = PurchaseEntity(
        id = id,
        tickId = tickId,
        accountId = accountId,
        sourceItemId = "item-$id",
        name = "Milk",
        itemKey = itemKey,
        categoryId = null,
        boughtAt = boughtAt,
        deletedAt = deletedAt,
        pendingUpload = pendingUpload,
    )

    private fun outbox(tickId: String, accountId: String = "u1", listId: String = "l1", revision: Int = 1) =
        TickOutboxEntity(
            tickId = tickId,
            accountId = accountId,
            itemId = "item-$tickId",
            listId = listId,
            tickedAt = 1_000L,
            name = "Milk",
            categoryId = null,
            undoneAt = null,
            revision = revision,
        )

    @Test
    fun `keyStats counts live rows only and separates guest from account`() = runTest {
        purchases.upsert(
            listOf(
                purchase("a", boughtAt = 100),
                purchase("b", boughtAt = 300),
                purchase("c", boughtAt = 900, deletedAt = 950),
                purchase("d", itemKey = "eggs", boughtAt = 200),
                purchase("g", accountId = null, boughtAt = 500),
                purchase("o", accountId = "u2", boughtAt = 700),
            ),
        )

        assertEquals(
            listOf(KeyStatRow("eggs", 1, 200), KeyStatRow("milk", 2, 300)),
            purchases.keyStats("u1").first().sortedBy { it.itemKey },
        )
        assertEquals(listOf(KeyStatRow("milk", 1, 500)), purchases.keyStats(null).first())
    }

    @Test
    fun `live reads skip tombstones and other owners`() = runTest {
        purchases.upsert(
            listOf(
                purchase("a"),
                purchase("b", itemKey = "eggs"),
                purchase("c", deletedAt = 5),
                purchase("g", accountId = null),
            ),
        )

        assertEquals(listOf("a"), purchases.liveForKeys("u1", listOf("milk")).map { it.id })
        assertEquals(listOf("a", "b"), purchases.live("u1").map { it.id }.sorted())
        assertEquals(listOf("g"), purchases.live(null).map { it.id })
        assertEquals(listOf("g"), purchases.guestPurchases().map { it.id })
        assertEquals(4, purchases.all().size)
    }

    @Test
    fun `tick id lookups tombstone and delete`() = runTest {
        purchases.upsert(listOf(purchase("a", tickId = "t1"), purchase("b", tickId = "t2")))

        assertEquals(listOf("a"), purchases.byTickId("t1").map { it.id })
        purchases.tombstoneByTickId("t1", now = 77)
        assertEquals(77L, purchases.byTickId("t1").single().deletedAt)
        purchases.deleteByTickId("t1")
        assertTrue(purchases.byTickId("t1").isEmpty())
        assertEquals(1, purchases.byTickId("t2").size)
    }

    @Test
    fun `deleteUpTo keeps later rows and other accounts`() = runTest {
        purchases.upsert(
            listOf(
                purchase("old", boughtAt = 100),
                purchase("edge", boughtAt = 200),
                purchase("new", boughtAt = 300),
                purchase("other", accountId = "u2", boughtAt = 100),
                purchase("guest", accountId = null, boughtAt = 100),
            ),
        )

        purchases.deleteUpTo("u1", boughtAt = 200)

        assertEquals(listOf("guest", "new", "other"), purchases.all().map { it.id }.sorted())
    }

    @Test
    fun `deleteAllFor clears one owner including the guest`() = runTest {
        purchases.upsert(listOf(purchase("a"), purchase("g", accountId = null)))

        purchases.deleteAllFor(null)
        assertEquals(listOf("a"), purchases.all().map { it.id })
        purchases.deleteAllFor("u1")
        assertTrue(purchases.all().isEmpty())
    }

    @Test
    fun `pending uploads are listed limited counted and marked`() = runTest {
        purchases.upsert(
            listOf(
                purchase("a", pendingUpload = true, boughtAt = 1),
                purchase("b", pendingUpload = true, boughtAt = 2),
                purchase("c", pendingUpload = false),
                purchase("d", accountId = "u2", pendingUpload = true),
            ),
        )

        assertEquals(2, purchases.countPendingUploads("u1"))
        assertEquals(listOf("a"), purchases.pendingUploads("u1", limit = 1).map { it.id })

        purchases.markUploaded(listOf("a"))
        assertEquals(listOf("b"), purchases.pendingUploads("u1", limit = 10).map { it.id })

        purchases.markPendingUploadByTickIds(listOf("t-c"))
        assertEquals(2, purchases.countPendingUploads("u1"))
    }

    @Test
    fun `an undone purchase is neither listed nor counted as a pending upload`() = runTest {
        purchases.upsert(
            listOf(
                purchase("a", pendingUpload = true, boughtAt = 1),
                purchase("b", pendingUpload = true, boughtAt = 2, deletedAt = 5),
            ),
        )

        assertEquals(listOf("a"), purchases.pendingUploads("u1", limit = 10).map { it.id })
        assertEquals(1, purchases.countPendingUploads("u1"))
    }

    @Test
    fun `outbox rows round trip and replace`() = runTest {
        purchases.insertOutbox(outbox("t1"))
        purchases.insertOutbox(outbox("t1", revision = 2))
        purchases.insertOutbox(outbox("t2", accountId = "u2"))

        assertEquals(2, purchases.outboxByTickId("t1")?.revision)
        assertNull(purchases.outboxByTickId("missing"))
        assertEquals(listOf("t1"), purchases.outboxBatch("u1", limit = 10).map { it.tickId })
        assertEquals(1, purchases.countOutbox("u1"))
    }

    @Test
    fun `deleteOutboxIfRevision returns 0 when the revision moved`() = runTest {
        purchases.insertOutbox(outbox("t1", revision = 3))

        assertEquals(0, purchases.deleteOutboxIfRevision("t1", revision = 2))
        assertNotNull(purchases.outboxByTickId("t1"))
        assertEquals(1, purchases.deleteOutboxIfRevision("t1", revision = 3))
        assertNull(purchases.outboxByTickId("t1"))
    }

    @Test
    fun `outbox rows are found and deleted by list and by account`() = runTest {
        purchases.insertOutbox(outbox("t1", listId = "l1"))
        purchases.insertOutbox(outbox("t2", listId = "l2"))
        purchases.insertOutbox(outbox("t3", listId = "l3", accountId = "u2"))

        assertEquals(listOf("t1", "t2"), purchases.outboxForLists(listOf("l1", "l2")).map { it.tickId }.sorted())
        purchases.deleteOutboxForLists(listOf("l1"))
        assertNull(purchases.outboxByTickId("t1"))
        purchases.deleteOutboxFor("u1")
        assertEquals(0, purchases.countOutbox("u1"))
        assertEquals(1, purchases.countOutbox("u2"))
    }

    @Test
    fun `mutes are per account and replace on upsert`() = runTest {
        state.upsertMute(ItemMuteEntity("u1", "milk", keptInARow = 1, mutedUntil = null))
        state.upsertMute(ItemMuteEntity("u1", "milk", keptInARow = 2, mutedUntil = 99))
        state.upsertMute(ItemMuteEntity("u2", "milk", keptInARow = 1, mutedUntil = null))

        assertEquals(listOf(ItemMuteEntity("u1", "milk", 2, 99)), state.mutes("u1"))
    }

    @Test
    fun `insertImpressionIfAbsent returns -1 the second time`() = runTest {
        val row = HintImpressionEntity("i1", "u1", "milk", shownAt = 10, holdout = false)

        assertTrue(state.insertImpressionIfAbsent(row) != -1L)
        assertEquals(-1L, state.insertImpressionIfAbsent(row.copy(shownAt = 20)))
        assertEquals(10L, state.impression("i1")?.shownAt)
        state.deleteImpression("i1")
        assertNull(state.impression("i1"))
    }

    @Test
    fun `pruneImpressions drops rows older than the cutoff`() = runTest {
        state.insertImpressionIfAbsent(HintImpressionEntity("old", "u1", "milk", 10, false))
        state.insertImpressionIfAbsent(HintImpressionEntity("new", "u1", "milk", 100, false))

        state.pruneImpressions(olderThan = 50)

        assertNull(state.impression("old"))
        assertNotNull(state.impression("new"))
    }

    @Test
    fun `follow ups insert once per week and delete by key`() = runTest {
        state.insertFollowUp(ReminderFollowUpEntity("u1", "2026-W45", 1, "milk\neggs", false))
        state.insertFollowUp(ReminderFollowUpEntity("u1", "2026-W45", 2, "bread", true))
        state.insertFollowUp(ReminderFollowUpEntity("u1", "2026-W46", 3, "milk", false))

        assertEquals(listOf(1L, 3L), state.followUps("u1").map { it.postedAt }.sorted())
        assertEquals("milk\neggs", state.followUps("u1").first { it.isoWeek == "2026-W45" }.itemKeys)
        state.deleteFollowUp("u1", "2026-W45")
        assertEquals(listOf("2026-W46"), state.followUps("u1").map { it.isoWeek })
    }

    @Test
    fun `moveGuestState rewrites empty account rows`() = runTest {
        state.upsertMute(ItemMuteEntity("", "milk", 1, null))
        state.insertImpressionIfAbsent(HintImpressionEntity("i1", "", "milk", 10, false))
        state.insertFollowUp(ReminderFollowUpEntity("", "2026-W45", 1, "milk", false))
        state.upsertMute(ItemMuteEntity("u1", "milk", 2, null))

        state.moveGuestState("u1")

        assertEquals(emptyList<ItemMuteEntity>(), state.mutes(""))
        assertEquals(1, state.mutes("u1").size)
        assertEquals("u1", state.impression("i1")?.accountId)
        assertEquals(listOf("u1"), state.followUps("u1").map { it.accountId })
    }

    @Test
    fun `clearState empties all three tables for one account`() = runTest {
        state.upsertMute(ItemMuteEntity("u1", "milk", 1, null))
        state.insertImpressionIfAbsent(HintImpressionEntity("i1", "u1", "milk", 10, false))
        state.insertFollowUp(ReminderFollowUpEntity("u1", "2026-W45", 1, "milk", false))
        state.upsertMute(ItemMuteEntity("u2", "milk", 1, null))

        state.clearState("u1")

        assertTrue(state.mutes("u1").isEmpty())
        assertNull(state.impression("i1"))
        assertTrue(state.followUps("u1").isEmpty())
        assertEquals(1, state.mutes("u2").size)
    }

    @Test
    fun `setPickedUp writes the tick time and keeps it on untick`() = runTest {
        dbRule.listDao.insertList(TestData.listEntity(id = "l1"))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "l1"))

        dbRule.itemDao.setPickedUp("i1", true, pickedUpAt = 500, updatedAt = 600, pendingSync = true)
        dbRule.itemDao.setPickedUp("i1", false, pickedUpAt = 500, updatedAt = 700, pendingSync = true)

        val item = dbRule.itemDao.getItemRaw("i1")!!
        assertEquals(false, item.isPickedUp)
        assertEquals(500L, item.pickedUpAt)
        assertEquals(700L, item.updatedAt)
        assertEquals(true, item.pendingSync)
    }

    @Test
    fun `uncheckedNames skips ticked deleted and other owners items`() = runTest {
        val lists = dbRule.listDao
        val items = dbRule.itemDao
        lists.insertList(TestData.listEntity(id = "guest"))
        lists.insertList(TestData.listEntity(id = "mine").copy(accountId = "u1"))
        lists.insertList(TestData.listEntity(id = "theirs").copy(accountId = "u2"))
        lists.insertList(TestData.listEntity(id = "gone").copy(accountId = "u1", deletedAt = 5))
        items.insertItem(TestData.itemEntity(id = "a", listId = "mine", name = "Milk"))
        items.insertItem(TestData.itemEntity(id = "b", listId = "mine", name = "Eggs", isPickedUp = true))
        items.insertItem(TestData.itemEntity(id = "c", listId = "mine", name = "Bread").copy(deletedAt = 5))
        items.insertItem(TestData.itemEntity(id = "d", listId = "theirs", name = "Tea"))
        items.insertItem(TestData.itemEntity(id = "e", listId = "gone", name = "Rice"))
        items.insertItem(TestData.itemEntity(id = "f", listId = "guest", name = "Jam"))

        assertEquals(listOf("Milk"), items.uncheckedNames("u1").first())
        assertEquals(listOf("Jam"), items.uncheckedNames(null).first())
    }
}
