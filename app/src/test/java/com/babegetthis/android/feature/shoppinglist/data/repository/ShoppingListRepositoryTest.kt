package com.babegetthis.android.feature.shoppinglist.data.repository

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Real in-memory database rather than mocked DAOs — see InMemoryDatabaseRule.
@RunWith(RobolectricTestRunner::class)
class ShoppingListRepositoryTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val repository by lazy {
        ShoppingListRepository(dbRule.listDao, dbRule.itemDao, dbRule.categoryDao)
    }

    private fun <T> Result<T>.data(): T = (this as Result.Success).data
    private fun <T> Result<T>.error(): AppError = (this as Result.Error).error

    // --- createList ---

    @Test
    fun `createList stores the list and returns its id`() = runTest {
        val id = repository.createList("Groceries").data()

        val stored = repository.getAllLists().first().single()
        assertEquals(id, stored.id)
        assertEquals("Groceries", stored.name)
    }

    @Test
    fun `createList trims the name`() = runTest {
        repository.createList("  Groceries  ")

        assertEquals("Groceries", repository.getAllLists().first().single().name)
    }

    @Test
    fun `createList rejects a blank name`() = runTest {
        val error = repository.createList("   ").error()

        assertTrue(error is AppError.ValidationError)
        assertTrue("nothing should have been stored", repository.getAllLists().first().isEmpty())
    }

    @Test
    fun `createList rejects an empty name`() = runTest {
        assertTrue(repository.createList("").error() is AppError.ValidationError)
    }

    // --- createListWithItems / draftsToItems ---

    @Test
    fun `createListWithItems stores the list and its drafts`() = runTest {
        val id = repository.createListWithItems(
            name = "Voice list",
            drafts = listOf(TestData.draft(name = "Milk"), TestData.draft(name = "Eggs")),
        ).data()

        val items = dbRule.itemDao.getItemsByListIdOnce(id)
        assertEquals(setOf("Milk", "Eggs"), items.map { it.name }.toSet())
    }

    @Test
    fun `createListWithItems accepts no drafts`() = runTest {
        val id = repository.createListWithItems("Empty", emptyList()).data()

        assertTrue(dbRule.itemDao.getItemsByListIdOnce(id).isEmpty())
    }

    @Test
    fun `draft quantity null becomes empty string to match typed items`() = runTest {
        val id = repository.createListWithItems(
            "L", listOf(TestData.draft(name = "Milk", quantity = null)),
        ).data()

        assertEquals("", dbRule.itemDao.getItemsByListIdOnce(id).single().quantity)
    }

    // The trust-boundary guard: the backend supplies a category id, and we keep
    // it ONLY if it names a row we actually have.
    @Test
    fun `known draft category is kept`() = runTest {
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "cat-dairy"))

        val id = repository.createListWithItems(
            "L", listOf(TestData.draft(name = "Milk", category = "cat-dairy")),
        ).data()

        assertEquals("cat-dairy", dbRule.itemDao.getItemsByListIdOnce(id).single().categoryId)
    }

    @Test
    fun `unknown draft category is dropped`() = runTest {
        val id = repository.createListWithItems(
            "L", listOf(TestData.draft(name = "Milk", category = "cat-not-real")),
        ).data()

        assertNull(dbRule.itemDao.getItemsByListIdOnce(id).single().categoryId)
    }

    @Test
    fun `null draft category stays null`() = runTest {
        val id = repository.createListWithItems(
            "L", listOf(TestData.draft(name = "Milk", category = null)),
        ).data()

        assertNull(dbRule.itemDao.getItemsByListIdOnce(id).single().categoryId)
    }

    @Test
    fun `draft note and shop are carried through`() = runTest {
        val id = repository.createListWithItems(
            "L", listOf(TestData.draft(name = "Milk", note = "semi-skimmed", shop = "Aldi")),
        ).data()

        val item = dbRule.itemDao.getItemsByListIdOnce(id).single()
        assertEquals("semi-skimmed", item.note)
        assertEquals("Aldi", item.shop)
    }

    @Test
    fun `createListWithItems rejects a blank name before writing anything`() = runTest {
        val error = repository.createListWithItems("  ", listOf(TestData.draft())).error()

        assertTrue(error is AppError.ValidationError)
        assertTrue(repository.getAllLists().first().isEmpty())
    }

    // --- addItemsToList ---

    @Test
    fun `addItemsToList appends to an existing list`() = runTest {
        val id = repository.createList("Groceries").data()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "existing", listId = id))

        repository.addItemsToList(id, listOf(TestData.draft(name = "Eggs")))

        assertEquals(2, dbRule.itemDao.getItemsByListIdOnce(id).size)
    }

    @Test
    fun `addItemsToList returns the list id`() = runTest {
        val id = repository.createList("Groceries").data()

        assertEquals(id, repository.addItemsToList(id, listOf(TestData.draft())).data())
    }

    @Test
    fun `addItemsToList with no drafts is a no-op`() = runTest {
        val id = repository.createList("Groceries").data()

        repository.addItemsToList(id, emptyList())

        assertTrue(dbRule.itemDao.getItemsByListIdOnce(id).isEmpty())
    }

    // --- updateListName ---

    @Test
    fun `updateListName writes the new name`() = runTest {
        val id = repository.createList("Before").data()

        repository.updateListName(id, "After")

        assertEquals("After", repository.getAllLists().first().single().name)
    }

    @Test
    fun `updateListName trims`() = runTest {
        val id = repository.createList("Before").data()

        repository.updateListName(id, "  After  ")

        assertEquals("After", repository.getAllLists().first().single().name)
    }

    @Test
    fun `updateListName rejects a blank name`() = runTest {
        val id = repository.createList("Before").data()

        assertTrue(repository.updateListName(id, " ").error() is AppError.ValidationError)
        assertEquals("Before", repository.getAllLists().first().single().name)
    }

    // Used to throw a bare IllegalStateException, which safeCall turned into an
    // UnknownError carrying "List not found" straight to a user-facing snackbar.
    @Test
    fun `updateListName reports NotFound for a missing list`() = runTest {
        assertTrue(repository.updateListName("nope", "X").error() is AppError.NotFoundError)
    }

    // --- lock state ---

    @Test
    fun `setLocked marks a single list`() = runTest {
        val id = repository.createList("Groceries").data()

        repository.setLocked(id, true)

        assertTrue(repository.getListById(id).first()!!.isLocked)
    }

    @Test
    fun `setLocked can unlock again`() = runTest {
        val id = repository.createList("Groceries").data()
        repository.setLocked(id, true)

        repository.setLocked(id, false)

        assertFalse(repository.getListById(id).first()!!.isLocked)
    }

    @Test
    fun `lockedCount counts locked lists only`() = runTest {
        val a = repository.createList("A").data()
        repository.createList("B")
        repository.setLocked(a, true)

        assertEquals(1, repository.lockedCount())
    }

    @Test
    fun `unlockAll clears every lock`() = runTest {
        val a = repository.createList("A").data()
        val b = repository.createList("B").data()
        repository.setLocked(a, true)
        repository.setLocked(b, true)

        repository.unlockAll()

        assertEquals(0, repository.lockedCount())
    }

    // --- delete / restore ---

    @Test
    fun `deleteListAndCaptureItems returns the items it is about to destroy`() = runTest {
        val id = repository.createList("Groceries").data()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", listId = id))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i2", listId = id))

        val captured = repository.deleteListAndCaptureItems(id).data()

        assertEquals(setOf("i1", "i2"), captured.map { it.id }.toSet())
        assertTrue(repository.getAllLists().first().isEmpty())
        assertTrue("CASCADE should have removed them", dbRule.itemDao.getItemsByListIdOnce(id).isEmpty())
    }

    @Test
    fun `deleteListAndCaptureItems on an empty list returns nothing`() = runTest {
        val id = repository.createList("Groceries").data()

        assertTrue(repository.deleteListAndCaptureItems(id).data().isEmpty())
    }

    @Test
    fun `deleteListIfEmpty reports true when it removed the list`() = runTest {
        val id = repository.createList("Groceries").data()

        assertEquals(true, repository.deleteListIfEmpty(id).data())
    }

    @Test
    fun `deleteListIfEmpty reports false when the list has items`() = runTest {
        val id = repository.createList("Groceries").data()
        dbRule.itemDao.insertItem(TestData.itemEntity(listId = id))

        assertEquals(false, repository.deleteListIfEmpty(id).data())
    }

    // The whole point of the undo cache: the list AND its items come back, so the
    // derived completion state recomputes correctly.
    @Test
    fun `restoreListWithItems brings back the list and its items`() = runTest {
        val id = repository.createList("Groceries").data()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", listId = id, isPickedUp = true))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i2", listId = id, isPickedUp = false))
        val original = repository.getAllLists().first().single()
        val captured = repository.deleteListAndCaptureItems(id).data()

        repository.restoreListWithItems(original, captured)

        val restored = repository.getAllLists().first().single()
        assertEquals(id, restored.id)
        assertEquals(2, restored.itemCount)
        assertEquals(1, restored.completedItemCount)
    }

    @Test
    fun `restoreListWithItems handles a list that had no items`() = runTest {
        val id = repository.createList("Groceries").data()
        val original = repository.getAllLists().first().single()
        repository.deleteListAndCaptureItems(id)

        repository.restoreListWithItems(original, emptyList())

        assertEquals(id, repository.getAllLists().first().single().id)
    }

    // --- flows ---

    @Test
    fun `getAllLists is empty to start`() = runTest {
        assertTrue(repository.getAllLists().first().isEmpty())
    }

    @Test
    fun `getListById emits null for a missing id`() = runTest {
        assertNull(repository.getListById("nope").first())
    }

    // --- failure mapping ---

    // A real constraint violation: the items reference a list that doesn't exist,
    // so the foreign key rejects them. safeCall must turn the SQLiteException
    // into a DatabaseError rather than letting it escape.
    //
    // (Closing the database instead would NOT work as a failure injection — Room
    // cancels the coroutine, and safeCall deliberately rethrows
    // CancellationException rather than dressing cancellation up as an error.)
    @Test
    fun `a database constraint failure comes back as DatabaseError`() = runTest {
        val result = repository.addItemsToList("no-such-list", listOf(TestData.draft()))

        assertTrue("expected an error, got $result", result is Result.Error)
        assertTrue(result.error() is AppError.DatabaseError)
    }

    @Test
    fun `a failed write leaves nothing behind`() = runTest {
        repository.addItemsToList("no-such-list", listOf(TestData.draft()))

        assertTrue(dbRule.itemDao.getItemsByListIdOnce("no-such-list").isEmpty())
    }
}
