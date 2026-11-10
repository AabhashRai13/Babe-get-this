package com.babegetthis.android.feature.gotthis.data

import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.feature.gotthis.domain.GotThisIds
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.InMemoryDatabaseRule
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TickStoreTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val hour = 60 * 60 * 1000L
    private var now = 1_793_527_200_000L
    private val clock = ReminderClock { now }
    private val store by lazy { TickStore(dbRule.db, dbRule.itemDao, dbRule.listDao, dbRule.purchaseDao, clock) }

    private suspend fun seed(accountId: String? = "u1", name: String = "Milk", pickedUpAt: Long? = null) {
        dbRule.listDao.insertList(
            ShoppingListEntity(
                id = "list-1",
                name = "Groceries",
                createdAt = 1L,
                updatedAt = 2L,
                accountId = accountId,
            ),
        )
        dbRule.itemDao.insertItem(
            ShoppingItemEntity(
                id = "i1",
                listId = "list-1",
                name = name,
                quantity = "1",
                categoryId = "dairy-eggs",
                createdAt = 1L,
                updatedAt = 2L,
                isPickedUp = pickedUpAt != null,
                pickedUpAt = pickedUpAt,
            ),
        )
    }

    private suspend fun item() = dbRule.itemDao.getItemRaw("i1")!!
    private fun tickId(at: Long) = GotThisIds.tickId("i1", at)

    @Test
    fun `tick on an account list writes the item, a purchase and an outbox row`() = runTest {
        seed()
        val tickedAt = now

        val result = store.tick("i1")

        assertEquals(TickResult("list-1", true, "u1", ItemKey.of("Milk"), false), result)
        val item = item()
        assertTrue(item.isPickedUp)
        assertEquals(tickedAt, item.pickedUpAt)
        assertEquals(tickedAt, item.updatedAt)
        assertTrue(item.pendingSync)
        val tid = tickId(tickedAt)
        val purchase = dbRule.purchaseDao.byTickId(tid).single()
        assertEquals(GotThisIds.purchaseId("u1", tid), purchase.id)
        assertEquals("u1", purchase.accountId)
        assertEquals("i1", purchase.sourceItemId)
        assertEquals("Milk", purchase.name)
        assertEquals(ItemKey.of("Milk"), purchase.itemKey)
        assertEquals("dairy-eggs", purchase.categoryId)
        assertEquals(tickedAt, purchase.boughtAt)
        assertNull(purchase.deletedAt)
        assertFalse(purchase.pendingUpload)
        val outbox = dbRule.purchaseDao.outboxByTickId(tid)!!
        assertEquals("u1", outbox.accountId)
        assertEquals("list-1", outbox.listId)
        assertEquals("i1", outbox.itemId)
        assertEquals(tickedAt, outbox.tickedAt)
        assertEquals("Milk", outbox.name)
        assertEquals("dairy-eggs", outbox.categoryId)
        assertNull(outbox.undoneAt)
        assertEquals(0, outbox.revision)
    }

    @Test
    fun `tick on a guest list writes a guest purchase and no outbox row`() = runTest {
        seed(accountId = null)

        val result = store.tick("i1")

        assertEquals(TickResult("list-1", false, null, ItemKey.of("Milk"), false), result)
        assertFalse(item().pendingSync)
        val tid = tickId(now)
        val purchase = dbRule.purchaseDao.byTickId(tid).single()
        assertEquals(GotThisIds.guestPurchaseId(tid), purchase.id)
        assertNull(purchase.accountId)
        assertNull(dbRule.purchaseDao.outboxByTickId(tid))
    }

    @Test
    fun `untick after one hour tombstones the purchase and marks the outbox row`() = runTest {
        seed()
        val tickedAt = now
        store.tick("i1")
        now += hour

        val result = store.untick("i1")

        assertEquals(TickResult("list-1", true, "u1", ItemKey.of("Milk"), true), result)
        val item = item()
        assertFalse(item.isPickedUp)
        assertEquals(tickedAt, item.pickedUpAt)
        assertEquals(now, item.updatedAt)
        assertTrue(item.pendingSync)
        val tid = tickId(tickedAt)
        assertEquals(now, dbRule.purchaseDao.byTickId(tid).single().deletedAt)
        val outbox = dbRule.purchaseDao.outboxByTickId(tid)!!
        assertEquals(now, outbox.undoneAt)
        assertEquals(1, outbox.revision)
    }

    @Test
    fun `untick of an already sent tick inserts a new outbox row from the purchase`() = runTest {
        seed()
        val tickedAt = now
        store.tick("i1")
        val tid = tickId(tickedAt)
        dbRule.purchaseDao.deleteOutboxIfRevision(tid, 0)
        dbRule.itemDao.insertItem(item().copy(name = "Oat milk", categoryId = "snacks"))
        now += hour

        store.untick("i1")

        val outbox = dbRule.purchaseDao.outboxByTickId(tid)!!
        assertEquals("Milk", outbox.name)
        assertEquals("dairy-eggs", outbox.categoryId)
        assertEquals(tickedAt, outbox.tickedAt)
        assertEquals(now, outbox.undoneAt)
        assertEquals(0, outbox.revision)
        assertEquals("u1", outbox.accountId)
        assertEquals("list-1", outbox.listId)
    }

    @Test
    fun `untick of a partner's tick builds the outbox row from the current item`() = runTest {
        val tickedAt = now - hour
        seed(pickedUpAt = tickedAt)

        val result = store.untick("i1")

        assertTrue(result!!.undone)
        val tid = tickId(tickedAt)
        val outbox = dbRule.purchaseDao.outboxByTickId(tid)!!
        assertEquals("Milk", outbox.name)
        assertEquals("dairy-eggs", outbox.categoryId)
        assertEquals(tickedAt, outbox.tickedAt)
        assertEquals(now, outbox.undoneAt)
        assertEquals(0, outbox.revision)
        assertTrue(dbRule.purchaseDao.byTickId(tid).isEmpty())
    }

    @Test
    fun `untick on a guest list within two hours deletes the purchase`() = runTest {
        seed(accountId = null)
        val tickedAt = now
        store.tick("i1")
        now += hour

        val result = store.untick("i1")

        assertTrue(result!!.undone)
        assertFalse(result.synced)
        val tid = tickId(tickedAt)
        assertTrue(dbRule.purchaseDao.byTickId(tid).isEmpty())
        assertNull(dbRule.purchaseDao.outboxByTickId(tid))
        assertEquals(tickedAt, item().pickedUpAt)
    }

    @Test
    fun `untick after three hours keeps the purchase and the outbox row`() = runTest {
        seed()
        val tickedAt = now
        store.tick("i1")
        now += 3 * hour

        val result = store.untick("i1")

        assertFalse(result!!.undone)
        assertFalse(item().isPickedUp)
        val tid = tickId(tickedAt)
        assertNull(dbRule.purchaseDao.byTickId(tid).single().deletedAt)
        val outbox = dbRule.purchaseDao.outboxByTickId(tid)!!
        assertNull(outbox.undoneAt)
        assertEquals(0, outbox.revision)
    }

    @Test
    fun `untick with a tick time in the future is not an undo`() = runTest {
        seed()
        store.tick("i1")
        val tid = tickId(now)
        now -= hour

        val result = store.untick("i1")

        assertFalse(result!!.undone)
        assertNull(dbRule.purchaseDao.byTickId(tid).single().deletedAt)
        assertNull(dbRule.purchaseDao.outboxByTickId(tid)!!.undoneAt)
    }

    @Test
    fun `untick of an item with no tick time is not an undo`() = runTest {
        seed()
        dbRule.itemDao.setPickedUp("i1", true, null, 2L, false)

        val result = store.untick("i1")

        assertFalse(result!!.undone)
        assertFalse(item().isPickedUp)
        assertNull(item().pickedUpAt)
    }

    @Test
    fun `tick, a late untick and a second tick give two purchases and two outbox rows`() = runTest {
        seed()
        val first = now
        store.tick("i1")
        now += 3 * hour
        store.untick("i1")
        now += hour
        val second = now
        store.tick("i1")

        assertEquals(2, dbRule.purchaseDao.live("u1").size)
        assertEquals(2, dbRule.purchaseDao.countOutbox("u1"))
        assertEquals(second, item().pickedUpAt)
        assertEquals(1, dbRule.purchaseDao.byTickId(tickId(first)).size)
        assertEquals(1, dbRule.purchaseDao.byTickId(tickId(second)).size)
    }

    @Test
    fun `renaming after the tick leaves the snapshots with the old name`() = runTest {
        seed()
        val tickedAt = now
        store.tick("i1")
        dbRule.itemDao.insertItem(item().copy(name = "Oat milk"))

        val tid = tickId(tickedAt)
        assertEquals("Milk", dbRule.purchaseDao.byTickId(tid).single().name)
        assertEquals("Milk", dbRule.purchaseDao.outboxByTickId(tid)!!.name)
        now += hour
        assertEquals(ItemKey.of("Oat milk"), store.untick("i1")!!.itemKey)
        assertEquals("Milk", dbRule.purchaseDao.outboxByTickId(tid)!!.name)
    }

    @Test
    fun `a 600 character name is cut to 500 in the purchase and the outbox`() = runTest {
        seed(name = "a".repeat(600))
        val tickedAt = now

        store.tick("i1")

        val tid = tickId(tickedAt)
        assertEquals(500, dbRule.purchaseDao.byTickId(tid).single().name.length)
        assertEquals(500, dbRule.purchaseDao.outboxByTickId(tid)!!.name.length)
    }

    @Test
    fun `a partner tick on a long name is undone with a 500 character name`() = runTest {
        val tickedAt = now - hour
        seed(name = "b".repeat(600), pickedUpAt = tickedAt)

        store.untick("i1")

        assertEquals(500, dbRule.purchaseDao.outboxByTickId(tickId(tickedAt))!!.name.length)
    }

    @Test
    fun `an unknown item returns null and writes nothing`() = runTest {
        seed()

        assertNull(store.tick("ghost"))
        assertNull(store.untick("ghost"))

        assertTrue(dbRule.purchaseDao.all().isEmpty())
        assertEquals(0, dbRule.purchaseDao.countOutbox("u1"))
        assertFalse(item().isPickedUp)
    }

    @Test
    fun `a failure inside the transaction leaves the item unticked and no purchase`() = runTest {
        seed()
        val failing = spyk(dbRule.purchaseDao)
        coEvery { failing.insertOutbox(any()) } throws IllegalStateException("boom")
        val failingStore = TickStore(dbRule.db, dbRule.itemDao, dbRule.listDao, failing, clock)

        try {
            failingStore.tick("i1")
            throw AssertionError("expected the tick to fail")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }

        assertFalse(item().isPickedUp)
        assertNull(item().pickedUpAt)
        assertTrue(dbRule.purchaseDao.all().isEmpty())
    }

    @Test
    fun `a second tick writes nothing`() = runTest {
        seed()
        val tickedAt = now
        store.tick("i1")
        now += hour

        val result = store.tick("i1")

        assertEquals(TickResult("list-1", true, "u1", ItemKey.of("Milk"), false), result)
        assertEquals(tickedAt, item().pickedUpAt)
        assertEquals(1, dbRule.purchaseDao.all().size)
        assertEquals(1, dbRule.purchaseDao.countOutbox("u1"))
        assertEquals(0, dbRule.purchaseDao.outboxByTickId(tickId(tickedAt))!!.revision)
    }

    @Test
    fun `a second untick writes nothing`() = runTest {
        seed()
        val tickedAt = now
        store.tick("i1")
        now += hour
        val undoneAt = now
        store.untick("i1")
        now += hour / 2

        val result = store.untick("i1")

        assertEquals(TickResult("list-1", true, "u1", ItemKey.of("Milk"), false), result)
        val tid = tickId(tickedAt)
        assertEquals(undoneAt, dbRule.purchaseDao.byTickId(tid).single().deletedAt)
        val outbox = dbRule.purchaseDao.outboxByTickId(tid)!!
        assertEquals(undoneAt, outbox.undoneAt)
        assertEquals(1, outbox.revision)
        assertEquals(undoneAt, item().updatedAt)
    }

    @Test
    fun `untick of a never ticked item writes nothing`() = runTest {
        seed()

        val result = store.untick("i1")

        assertEquals(TickResult("list-1", true, "u1", ItemKey.of("Milk"), false), result)
        assertEquals(2L, item().updatedAt)
        assertTrue(dbRule.purchaseDao.all().isEmpty())
        assertEquals(0, dbRule.purchaseDao.countOutbox("u1"))
    }
}
