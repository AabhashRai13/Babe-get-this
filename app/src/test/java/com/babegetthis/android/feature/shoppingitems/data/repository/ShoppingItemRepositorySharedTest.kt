package com.babegetthis.android.feature.shoppingitems.data.repository

import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.InMemoryDatabaseRule
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
    private val repository by lazy {
        ShoppingItemRepository(dbRule.itemDao, dbRule.categoryDao, dbRule.listDao) { kicks++ }
    }

    private suspend fun seedList(shareCode: String? = "ABC234") = dbRule.listDao.insertList(
        ShoppingListEntity(
            id = "list-1", name = "Groceries", createdAt = 1L, updatedAt = 2L, shareCode = shareCode,
        )
    )

    private suspend fun domainItem() = repository.getItemsByListId("list-1").first().single()

    @Test
    fun `adding to a shared list marks the item dirty and kicks`() = runTest {
        seedList()

        repository.addItem("list-1", name = "Milk", quantity = "1")

        assertTrue(dbRule.itemDao.getPendingSyncItems().single().name == "Milk")
        assertEquals(1, kicks)
    }

    @Test
    fun `adding to a local-only list stays clean and quiet`() = runTest {
        seedList(shareCode = null)

        repository.addItem("list-1", name = "Milk", quantity = "1")

        assertTrue(dbRule.itemDao.getPendingSyncItems().isEmpty())
        assertEquals(0, kicks)
    }

    @Test
    fun `editing a shared item marks it dirty and kicks`() = runTest {
        seedList()
        repository.addItem("list-1", name = "Milk", quantity = "1")
        kicks = 0
        dbRule.itemDao.getPendingSyncItems().forEach { dbRule.itemDao.markItemSynced(it.id, it.updatedAt) }

        repository.updateItem(domainItem().copy(name = "Oat milk"))

        val row = dbRule.itemDao.getPendingSyncItems().single()
        assertEquals("Oat milk", row.name)
        assertEquals(1, kicks)
    }

    @Test
    fun `ticking a shared item marks it dirty and kicks`() = runTest {
        seedList()
        repository.addItem("list-1", name = "Milk", quantity = "1")
        val id = dbRule.itemDao.getItemsByListIdOnce("list-1").single().id
        kicks = 0
        dbRule.itemDao.getPendingSyncItems().forEach { dbRule.itemDao.markItemSynced(it.id, it.updatedAt) }

        repository.togglePickedUp(id, isPickedUp = true)

        val row = dbRule.itemDao.getItemRaw(id)!!
        assertTrue(row.isPickedUp)
        assertTrue(row.pendingSync)
        assertEquals(1, kicks)
    }

    @Test
    fun `ticking a local-only item stays clean and quiet`() = runTest {
        seedList(shareCode = null)
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
        seedList(shareCode = null)
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
}
