package com.babegetthis.android.feature.shoppinglist.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.TestData
import com.babegetthis.android.testing.authStateFor
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

// The shared-list write paths: dirty flags, soft deletes, and push kicks.
// Local-only behavior is covered by ShoppingListRepositoryTest and must not
// change — a few tests here pin exactly that.
@RunWith(RobolectricTestRunner::class)
class ShoppingListRepositorySharedTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private var kicks = 0
    private val repository by lazy {
        ShoppingListRepository(dbRule.listDao, dbRule.itemDao, dbRule.categoryDao, authStateFor("u1")) { kicks++ }
    }

    private fun sharedList(id: String = "list-1") = ShoppingListEntity(
        id = id,
        name = "Groceries",
        createdAt = 1L,
        updatedAt = 2L,
        shareCode = "ABC234",
        accountId = "u1",
    )

    private suspend fun seedShared(id: String = "list-1") = dbRule.listDao.insertList(sharedList(id))

    @Test
    fun `getShareCode reflects the row's live share state`() = runTest {
        seedShared()

        assertEquals("ABC234", repository.getShareCode("list-1").first())
        assertNull(repository.getShareCode("other").first())
    }

    // --- rename -------------------------------------------------------------

    @Test
    fun `renaming a shared list marks it dirty and kicks a push`() = runTest {
        seedShared()

        repository.updateListName("list-1", "Weekend shop")

        val row = dbRule.listDao.getListRaw("list-1")!!
        assertTrue(row.pendingSync)
        assertEquals("Weekend shop", row.name)
        assertEquals(1, kicks)
    }

    @Test
    fun `renaming a local-only list stays clean and quiet`() = runTest {
        dbRule.listDao.insertList(sharedList().copy(shareCode = null, accountId = null))

        repository.updateListName("list-1", "Weekend shop")

        assertFalse(dbRule.listDao.getListRaw("list-1")!!.pendingSync)
        assertEquals(0, kicks)
    }

    // --- voice add-to-list --------------------------------------------------

    @Test
    fun `voice items added to a shared list arrive dirty and kick a push`() = runTest {
        seedShared()

        repository.addItemsToList("list-1", listOf(TestData.draft(name = "Milk")))

        val item = dbRule.itemDao.getItemsByListIdOnce("list-1").single()
        assertTrue(item.pendingSync)
        assertEquals(1, kicks)
    }

    // --- delete / restore ---------------------------------------------------

    @Test
    fun `deleting a shared list soft-deletes and keeps the item rows`() = runTest {
        seedShared()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "list-1"))

        val captured = repository.deleteListAndCaptureItems("list-1")

        assertEquals(1, (captured as Result.Success).data.size)
        val row = dbRule.listDao.getListRaw("list-1")!!
        assertNotNull("tombstoned, not gone", row.deletedAt)
        assertTrue(row.pendingSync)
        assertNotNull("item rows survive behind the tombstone", dbRule.itemDao.getItemRaw("i1"))
        assertEquals(1, kicks)
    }

    @Test
    fun `deleting a local-only list still hard-deletes with cascade`() = runTest {
        dbRule.listDao.insertList(sharedList().copy(shareCode = null, accountId = null))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "list-1"))

        repository.deleteListAndCaptureItems("list-1")

        assertNull(dbRule.listDao.getListRaw("list-1"))
        assertNull("CASCADE wiped it", dbRule.itemDao.getItemRaw("i1"))
        assertEquals(0, kicks)
    }

    @Test
    fun `an empty shared list is never auto-deleted`() = runTest {
        seedShared()

        val deleted = repository.deleteListIfEmpty("list-1")

        assertEquals(Result.Success(false), deleted)
        assertNotNull(dbRule.listDao.getListRaw("list-1"))
    }

    @Test
    fun `undoing a shared delete clears the tombstone and keeps the share code`() = runTest {
        seedShared()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "list-1"))
        val captured = (repository.deleteListAndCaptureItems("list-1") as Result.Success).data

        repository.restoreListWithItems(
            list = ShoppingList(id = "list-1", name = "Groceries", createdAt = 1L, updatedAt = 2L),
            items = captured,
        )

        val row = dbRule.listDao.getListRaw("list-1")!!
        assertNull("alive again", row.deletedAt)
        assertEquals("share survives the undo round-trip", "ABC234", row.shareCode)
        assertTrue("revival must sync to members", row.pendingSync)
        assertNotNull("undo must not cascade the items away", dbRule.itemDao.getItemRaw("i1"))
        assertEquals("delete + restore", 2, kicks)
    }

    // --- unshared account lists sync too (docs/technical-decisions/008) ---

    private suspend fun seedAccountList() = dbRule.listDao.insertList(sharedList().copy(shareCode = null))

    @Test
    fun `deleting an unshared account list tombstones it and undo clears the tombstone`() = runTest {
        seedAccountList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "list-1"))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i2", listId = "list-1"))
        val captured = (repository.deleteListAndCaptureItems("list-1") as Result.Success).data
        assertNotNull("tombstoned, not gone", dbRule.listDao.getListRaw("list-1")!!.deletedAt)

        repository.restoreListWithItems(
            list = ShoppingList(id = "list-1", name = "Groceries", createdAt = 1L, updatedAt = 2L),
            items = captured,
        )

        val row = dbRule.listDao.getListRaw("list-1")!!
        assertNull(row.deletedAt)
        assertEquals("still the account's list", "u1", row.accountId)
        assertTrue(row.pendingSync)
        assertNotNull(dbRule.itemDao.getItemRaw("i1"))
        assertNotNull(dbRule.itemDao.getItemRaw("i2"))
        assertEquals(2, kicks)
    }

    @Test
    fun `renaming an unshared account list marks it dirty and kicks`() = runTest {
        seedAccountList()

        repository.updateListName("list-1", "Weekend shop")

        assertTrue(dbRule.listDao.getListRaw("list-1")!!.pendingSync)
        assertEquals(1, kicks)
    }

    @Test
    fun `an empty unshared account list is tombstoned, not deleted`() = runTest {
        seedAccountList()

        val deleted = repository.deleteListIfEmpty("list-1")

        assertEquals(Result.Success(true), deleted)
        val row = dbRule.listDao.getListRaw("list-1")!!
        assertNotNull("the deletion has to reach the account's other phones", row.deletedAt)
        assertTrue(row.pendingSync)
        assertEquals(1, kicks)
    }

    @Test
    fun `an unshared account list with items is kept`() = runTest {
        seedAccountList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "list-1"))

        val deleted = repository.deleteListIfEmpty("list-1")

        assertEquals(Result.Success(false), deleted)
        assertNull(dbRule.listDao.getListRaw("list-1")!!.deletedAt)
        assertEquals(0, kicks)
    }
}
