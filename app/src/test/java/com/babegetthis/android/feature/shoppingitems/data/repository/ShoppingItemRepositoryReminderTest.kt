package com.babegetthis.android.feature.shoppingitems.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeReminderKicker
import com.babegetthis.android.testing.InMemoryDatabaseRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Local item writes tell the reminder seam; the calls apply to guest and
// account lists alike. Sync apply writes DAOs directly and never reaches here.
@RunWith(RobolectricTestRunner::class)
class ShoppingItemRepositoryReminderTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val reminders = FakeReminderKicker()
    private val repository by lazy {
        ShoppingItemRepository(dbRule.itemDao, dbRule.categoryDao, dbRule.listDao, {}, reminders)
    }

    private suspend fun seedList(accountId: String? = null) = dbRule.listDao.insertList(
        ShoppingListEntity(id = "list-1", name = "Groceries", createdAt = 1L, updatedAt = 2L, accountId = accountId),
    )

    // Adds an item, then forgets the add's own onLocalEdit so a test sees only
    // the call it is about.
    private suspend fun seedItem(): String {
        val id = (repository.addItem("list-1", name = "Milk", quantity = "1") as Result.Success).data
        reminders.edits.clear()
        return id
    }

    @Test
    fun `ticking calls onLocalPickUp with the list id`() = runTest {
        seedList()
        val id = seedItem()

        repository.togglePickedUp(id, true)

        assertEquals(listOf("list-1"), reminders.pickUps)
        assertTrue(reminders.edits.isEmpty())
    }

    @Test
    fun `unticking calls onLocalEdit`() = runTest {
        seedList()
        val id = seedItem()

        repository.togglePickedUp(id, false)

        assertEquals(listOf("list-1"), reminders.edits)
        assertTrue(reminders.pickUps.isEmpty())
    }

    @Test
    fun `add, update, delete and restore call onLocalEdit`() = runTest {
        seedList()

        repository.addItem("list-1", name = "Milk", quantity = "1")
        assertEquals(listOf("list-1"), reminders.edits)

        val item = repository.getItemsByListId("list-1").first().single()
        repository.updateItem(item.copy(name = "Oat milk"))
        assertEquals(2, reminders.edits.size)

        repository.deleteItem(item.id)
        assertEquals(3, reminders.edits.size)

        repository.restoreItem(item)
        assertEquals(listOf("list-1", "list-1", "list-1", "list-1"), reminders.edits)
        assertTrue(reminders.pickUps.isEmpty())
    }

    @Test
    fun `deleting an account list item calls onLocalEdit after the tombstone`() = runTest {
        seedList(accountId = "u1")
        val id = seedItem()

        repository.deleteItem(id)

        assertEquals(listOf("list-1"), reminders.edits)
    }

    @Test
    fun `a tick on an unknown item calls nothing`() = runTest {
        seedList()

        repository.togglePickedUp("missing", true)
        repository.togglePickedUp("missing", false)

        assertTrue(reminders.pickUps.isEmpty())
        assertTrue(reminders.edits.isEmpty())
    }

    @Test
    fun `deleting an unknown item calls nothing`() = runTest {
        seedList()

        repository.deleteItem("missing")

        assertTrue(reminders.pickUps.isEmpty())
        assertTrue(reminders.edits.isEmpty())
    }
}
