package com.babegetthis.android.feature.shoppingitems.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.gotthis.data.TickResult
import com.babegetthis.android.feature.gotthis.data.TickStore
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeReminderKicker
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.unreportedSafeCall
import kotlinx.coroutines.flow.first
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

// Item writes on shared lists: dirty flags, soft delete, undo revival, and
// push kicks. Local-only behavior is pinned by ShoppingItemDataTest.
@RunWith(RobolectricTestRunner::class)
class ShoppingItemRepositorySharedTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private var kicks = 0
    private val reminders = FakeReminderKicker()
    private val tickStore by lazy {
        TickStore(dbRule.db, dbRule.itemDao, dbRule.listDao, dbRule.purchaseDao) { System.currentTimeMillis() }
    }
    private val repository by lazy {
        ShoppingItemRepository(dbRule.itemDao, dbRule.categoryDao, dbRule.listDao, {
            kicks++
        }, reminders, unreportedSafeCall(), tickStore)
    }

    // Defaults to a shared list of account u1. Sync is decided by the owner
    // now, not the share code (docs/technical-decisions/008).
    private suspend fun seedList(shareCode: String? = "ABC234", accountId: String? = "u1") = dbRule.listDao.insertList(
        ShoppingListEntity(
            id = "list-1",
            name = "Groceries",
            createdAt = 1L,
            updatedAt = 2L,
            shareCode = shareCode,
            accountId = accountId,
        ),
    )

    private suspend fun domainItem() = repository.getItemsByListId("list-1").first().single()

    @Test
    fun `adding to a shared list marks the item dirty and kicks`() = runTest {
        seedList()

        repository.addItem("list-1", name = "Milk", quantity = "1")

        assertTrue(dbRule.itemDao.getPendingSyncItemsForAccount("u1").single().name == "Milk")
        assertEquals(1, kicks)
    }

    @Test
    fun `adding to an unshared account list marks the item dirty and kicks`() = runTest {
        seedList(shareCode = null)

        repository.addItem("list-1", name = "Milk", quantity = "1")

        assertEquals("Milk", dbRule.itemDao.getPendingSyncItemsForAccount("u1").single().name)
        assertEquals(1, kicks)
    }

    @Test
    fun `adding to a local-only list stays clean and quiet`() = runTest {
        seedList(shareCode = null, accountId = null)

        repository.addItem("list-1", name = "Milk", quantity = "1")

        assertTrue(dbRule.itemDao.getPendingSyncItemsForAccount("u1").isEmpty())
        assertEquals(0, kicks)
    }

    @Test
    fun `editing a shared item marks it dirty and kicks`() = runTest {
        seedList()
        repository.addItem("list-1", name = "Milk", quantity = "1")
        kicks = 0
        dbRule.itemDao.getPendingSyncItemsForAccount("u1").forEach {
            dbRule.itemDao.markItemSynced(it.id, it.updatedAt)
        }

        repository.updateItem(domainItem().copy(name = "Oat milk"))

        val row = dbRule.itemDao.getPendingSyncItemsForAccount("u1").single()
        assertEquals("Oat milk", row.name)
        assertEquals(1, kicks)
    }

    @Test
    fun `ticking a shared item marks it dirty and kicks`() = runTest {
        seedList()
        repository.addItem("list-1", name = "Milk", quantity = "1")
        val id = dbRule.itemDao.getItemsByListIdOnce("list-1").single().id
        kicks = 0
        dbRule.itemDao.getPendingSyncItemsForAccount("u1").forEach {
            dbRule.itemDao.markItemSynced(it.id, it.updatedAt)
        }

        repository.togglePickedUp(id, isPickedUp = true)

        val row = dbRule.itemDao.getItemRaw(id)!!
        assertTrue(row.isPickedUp)
        assertTrue(row.pendingSync)
        assertEquals(1, kicks)
    }

    @Test
    fun `ticking a local-only item stays clean and quiet`() = runTest {
        seedList(shareCode = null, accountId = null)
        repository.addItem("list-1", name = "Milk", quantity = "1")
        val id = dbRule.itemDao.getItemsByListIdOnce("list-1").single().id

        repository.togglePickedUp(id, isPickedUp = true)

        assertFalse(dbRule.itemDao.getItemRaw(id)!!.pendingSync)
        assertEquals(0, kicks)
    }

    @Test
    fun `ticking a missing item is a quiet no-op`() = runTest {
        repository.togglePickedUp("ghost", isPickedUp = true)

        assertEquals(0, kicks)
    }

    @Test
    fun `deleting a shared item tombstones it and kicks`() = runTest {
        seedList()
        repository.addItem("list-1", name = "Milk", quantity = "1")
        val id = dbRule.itemDao.getItemsByListIdOnce("list-1").single().id
        kicks = 0

        repository.deleteItem(id)

        val row = dbRule.itemDao.getItemRaw(id)!!
        assertNotNull("soft-deleted, row survives", row.deletedAt)
        assertTrue(row.pendingSync)
        assertTrue("hidden from the UI query", dbRule.itemDao.getItemsByListIdOnce("list-1").isEmpty())
        assertEquals(1, kicks)
    }

    @Test
    fun `deleting a local-only item still hard-deletes`() = runTest {
        seedList(shareCode = null, accountId = null)
        repository.addItem("list-1", name = "Milk", quantity = "1")
        val id = dbRule.itemDao.getItemsByListIdOnce("list-1").single().id

        repository.deleteItem(id)

        assertNull("row actually gone", dbRule.itemDao.getItemRaw(id))
        assertEquals(0, kicks)
    }

    @Test
    fun `undoing a shared item delete revives the tombstoned row`() = runTest {
        seedList()
        repository.addItem("list-1", name = "Milk", quantity = "1")
        val item = domainItem()
        repository.deleteItem(item.id)
        kicks = 0

        repository.restoreItem(item)

        val row = dbRule.itemDao.getItemRaw(item.id)!!
        assertNull("alive again", row.deletedAt)
        assertTrue("revival must sync to members", row.pendingSync)
        assertEquals(1, kicks)
    }

    // A full-row write must carry the tick time, or the item looks never ticked.
    @Test
    fun `editing a ticked item keeps its tick time`() = runTest {
        seedList()
        val id = (repository.addItem("list-1", name = "Milk", quantity = "1") as Result.Success).data
        repository.togglePickedUp(id, isPickedUp = true)
        val tickedAt = dbRule.itemDao.getItemRaw(id)!!.pickedUpAt
        assertNotNull(tickedAt)

        repository.updateItem(domainItem().copy(quantity = "2"))

        val row = dbRule.itemDao.getItemRaw(id)!!
        assertEquals("2", row.quantity)
        assertTrue(row.isPickedUp)
        assertEquals(tickedAt, row.pickedUpAt)
    }

    @Test
    fun `undoing the delete of a ticked item keeps its tick time`() = runTest {
        seedList()
        val id = (repository.addItem("list-1", name = "Milk", quantity = "1") as Result.Success).data
        repository.togglePickedUp(id, isPickedUp = true)
        val tickedAt = dbRule.itemDao.getItemRaw(id)!!.pickedUpAt
        val item = domainItem()
        repository.deleteItem(id)

        repository.restoreItem(item)

        val row = dbRule.itemDao.getItemRaw(id)!!
        assertNull(row.deletedAt)
        assertEquals(tickedAt, row.pickedUpAt)
    }

    @Test
    fun `togglePickedUp on an account list returns the tick result and records the purchase`() = runTest {
        seedList()
        val id = (repository.addItem("list-1", name = "Milk", quantity = "1") as Result.Success).data
        kicks = 0

        val result = (repository.togglePickedUp(id, isPickedUp = true) as Result.Success).data

        assertEquals(TickResult("list-1", true, "u1", ItemKey.of("Milk"), false, recorded = true), result)
        assertEquals(1, dbRule.purchaseDao.live("u1").size)
        assertEquals(1, kicks)
    }

    @Test
    fun `togglePickedUp of an unknown item succeeds with null and kicks nothing`() = runTest {
        val result = repository.togglePickedUp("ghost", isPickedUp = true)

        assertNull((result as Result.Success).data)
        assertEquals(0, kicks)
        assertTrue(reminders.pickUps.isEmpty() && reminders.edits.isEmpty())
    }
}
