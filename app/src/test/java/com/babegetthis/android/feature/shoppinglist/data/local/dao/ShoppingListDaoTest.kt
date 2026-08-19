package com.babegetthis.android.feature.shoppinglist.data.local.dao

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

// Runs against a real in-memory SQLite database, not a mocked DAO. The behavior
// worth testing here IS the SQL — the join-and-aggregate, the CASCADE, and the
// atomic check-and-delete. A mocked DAO would only prove we call the method we
// wrote.
@RunWith(RobolectricTestRunner::class)
class ShoppingListDaoTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val listDao get() = dbRule.listDao
    private val itemDao get() = dbRule.itemDao

    @Test
    fun `getAllListsWithItemCount counts items and completions`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1"))
        itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "l1", isPickedUp = true))
        itemDao.insertItem(TestData.itemEntity(id = "i2", listId = "l1", isPickedUp = true))
        itemDao.insertItem(TestData.itemEntity(id = "i3", listId = "l1", isPickedUp = false))

        val row = listDao.getAllListsWithItemCount().first().single()

        assertEquals(3, row.itemCount)
        assertEquals(2, row.completedItemCount)
    }

    // The LEFT JOIN produces one all-null row for a list with no items, so both
    // aggregates must come back as 0 rather than null or 1.
    @Test
    fun `list with no items reports zero counts`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1"))

        val row = listDao.getAllListsWithItemCount().first().single()

        assertEquals(0, row.itemCount)
        assertEquals(0, row.completedItemCount)
    }

    @Test
    fun `lists are ordered newest first`() = runTest {
        listDao.insertList(TestData.listEntity(id = "old", createdAt = TestData.T0))
        listDao.insertList(TestData.listEntity(id = "new", createdAt = TestData.T0 + TestData.DAY))

        val ids = listDao.getAllListsWithItemCount().first().map { it.id }

        assertEquals(listOf("new", "old"), ids)
    }

    @Test
    fun `counts are scoped per list`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1"))
        listDao.insertList(TestData.listEntity(id = "l2"))
        itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "l1"))
        itemDao.insertItem(TestData.itemEntity(id = "i2", listId = "l2"))
        itemDao.insertItem(TestData.itemEntity(id = "i3", listId = "l2"))

        val byId = listDao.getAllListsWithItemCount().first().associateBy { it.id }

        assertEquals(1, byId.getValue("l1").itemCount)
        assertEquals(2, byId.getValue("l2").itemCount)
    }

    @Test
    fun `getListById emits null for a missing id`() = runTest {
        assertNull(listDao.getListById("nope").first())
    }

    @Test
    fun `getListById returns the stored row`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1", name = "Hardware"))

        assertEquals("Hardware", listDao.getListById("l1").first()?.name)
    }

    @Test
    fun `setLocked toggles a single list`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1"))
        listDao.insertList(TestData.listEntity(id = "l2"))

        listDao.setLocked("l1", true)

        assertTrue(listDao.getListById("l1").first()!!.isLocked)
        assertFalse(listDao.getListById("l2").first()!!.isLocked)
    }

    @Test
    fun `unlockAll clears every locked list`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1", isLocked = true))
        listDao.insertList(TestData.listEntity(id = "l2", isLocked = true))
        listDao.insertList(TestData.listEntity(id = "l3", isLocked = false))

        listDao.unlockAll()

        assertTrue(listDao.getAllListsWithItemCount().first().none { it.isLocked })
    }

    @Test
    fun `lockedCount counts only locked lists`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1", isLocked = true))
        listDao.insertList(TestData.listEntity(id = "l2", isLocked = false))
        listDao.insertList(TestData.listEntity(id = "l3", isLocked = true))

        assertEquals(2, listDao.lockedCount())
    }

    @Test
    fun `lockedCount is zero with no lists`() = runTest {
        assertEquals(0, listDao.lockedCount())
    }

    // The undo-delete flow depends entirely on this: deleting the list must take
    // its items with it, which is why the rule turns foreign keys on.
    @Test
    fun `deleting a list cascades to its items`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1"))
        itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "l1"))
        itemDao.insertItem(TestData.itemEntity(id = "i2", listId = "l1"))

        listDao.deleteList("l1")

        assertTrue(itemDao.getItemsByListIdOnce("l1").isEmpty())
    }

    @Test
    fun `deleteListIfEmpty removes an empty list and reports one row`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1"))

        assertEquals(1, listDao.deleteListIfEmpty("l1"))
        assertNull(listDao.getListById("l1").first())
    }

    @Test
    fun `deleteListIfEmpty keeps a list that has items`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1"))
        itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "l1"))

        assertEquals(0, listDao.deleteListIfEmpty("l1"))
        assertEquals("l1", listDao.getListById("l1").first()?.id)
    }

    @Test
    fun `deleteListIfEmpty reports zero for a list that does not exist`() = runTest {
        assertEquals(0, listDao.deleteListIfEmpty("nope"))
    }

    @Test
    fun `insertList replaces on conflicting id`() = runTest {
        listDao.insertList(TestData.listEntity(id = "l1", name = "First"))
        listDao.insertList(TestData.listEntity(id = "l1", name = "Second"))

        val rows = listDao.getAllListsWithItemCount().first()
        assertEquals(1, rows.size)
        assertEquals("Second", rows.single().name)
    }

    @Test
    fun `updateList writes the new name`() = runTest {
        val entity = TestData.listEntity(id = "l1", name = "Before")
        listDao.insertList(entity)

        listDao.updateList(entity.copy(name = "After"))

        assertEquals("After", listDao.getListById("l1").first()?.name)
    }

    @Test
    fun `insertListWithItems writes both in one call`() = runTest {
        listDao.insertListWithItems(
            list = TestData.listEntity(id = "l1"),
            items = listOf(
                TestData.itemEntity(id = "i1", listId = "l1"),
                TestData.itemEntity(id = "i2", listId = "l1"),
            ),
        )

        assertEquals("l1", listDao.getListById("l1").first()?.id)
        assertEquals(2, itemDao.getItemsByListIdOnce("l1").size)
    }

    @Test
    fun `insertListWithItems accepts an empty item list`() = runTest {
        listDao.insertListWithItems(TestData.listEntity(id = "l1"), emptyList())

        assertEquals("l1", listDao.getListById("l1").first()?.id)
        assertTrue(itemDao.getItemsByListIdOnce("l1").isEmpty())
    }

    // Proves it really is one transaction: the items reference a list id that
    // doesn't exist, so the foreign key rejects them — and the list insert must
    // roll back with them rather than leaving a stray empty list behind.
    @Test
    fun `insertListWithItems rolls back the list when its items are rejected`() = runTest {
        val failed = runCatching {
            listDao.insertListWithItems(
                list = TestData.listEntity(id = "l1"),
                items = listOf(TestData.itemEntity(id = "i1", listId = "does-not-exist")),
            )
        }.isFailure

        assertTrue("expected the foreign key to reject the item", failed)
        assertNull("the list insert should have rolled back", listDao.getListById("l1").first())
    }
}
