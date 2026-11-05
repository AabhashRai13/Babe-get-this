package com.babegetthis.android.core.reminder.data.local

import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.TestData
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

// Runs against a real in-memory database: the tombstone filtering and the
// single-transaction read are the behavior worth testing, and a mock would
// only echo them back.
@RunWith(RobolectricTestRunner::class)
class ReminderContentReaderTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val reader by lazy { ReminderContentReader(dbRule.db) }
    private val eligibility by lazy { ReminderEligibility(reader) }

    private suspend fun seedList(id: String = "list-1") = dbRule.listDao.insertList(TestData.listEntity(id = id))

    private suspend fun seedItem(
        id: String,
        name: String,
        picked: Boolean,
        listId: String = "list-1",
        shop: String? = null,
        categoryId: String? = null,
    ) = dbRule.itemDao.insertItem(
        TestData.itemEntity(
            id = id,
            listId = listId,
            name = name,
            isPickedUp = picked,
            shop = shop,
            categoryId = categoryId,
        ),
    )

    @Test
    fun `snapshot is null for a missing list`() = runTest {
        assertNull(reader.snapshot("nope"))
    }

    @Test
    fun `snapshot is null for a tombstoned list even with live items`() = runTest {
        dbRule.listDao.insertList(TestData.listEntity().copy(deletedAt = 5L))
        seedItem("a", "Milk", picked = true)
        seedItem("b", "Eggs", picked = false)

        assertNull(reader.snapshot("list-1"))
    }

    @Test
    fun `snapshot excludes tombstoned items from counts and names`() = runTest {
        seedList()
        seedItem("a", "Milk", picked = true)
        seedItem("b", "Eggs", picked = false)
        dbRule.itemDao.insertItem(
            TestData.itemEntity(id = "c", name = "Ghost picked", isPickedUp = true).copy(deletedAt = 5L),
        )
        dbRule.itemDao.insertItem(
            TestData.itemEntity(id = "d", name = "Ghost unpicked", isPickedUp = false).copy(deletedAt = 5L),
        )

        val content = reader.snapshot("list-1")

        assertNotNull(content)
        assertEquals(1, content!!.pickedCount)
        assertEquals(listOf("Eggs"), content.uncheckedNames)
        assertEquals(1, content.uncheckedCount)
    }

    // Inserted in an order that differs from the screen's (shop, then category,
    // then name, nulls last), so a read in insertion order would fail.
    @Test
    fun `snapshot names come in the items screen's order and count the picked ones`() = runTest {
        seedList()
        seedItem("a", "Apples", picked = false)
        seedItem("b", "Zucchini", picked = false, shop = "Aldi", categoryId = "veg")
        seedItem("c", "Bread", picked = false, shop = "Aldi")
        seedItem("d", "Milk", picked = true, shop = "Aldi", categoryId = "dairy")
        seedItem("e", "Carrots", picked = false, shop = "Aldi", categoryId = "veg")
        seedItem("f", "Eggs", picked = false, shop = "Costco", categoryId = "dairy")
        seedItem("g", "Rice", picked = true)

        val content = reader.snapshot("list-1")!!
        val screenOrder = dbRule.itemDao.getItemsByListId("list-1").first().filterNot { it.isPickedUp }.map { it.name }

        assertEquals("list-1", content.listId)
        assertEquals("Groceries", content.listName)
        assertEquals(2, content.pickedCount)
        assertEquals(5, content.uncheckedCount)
        assertEquals(listOf("Carrots", "Zucchini", "Bread", "Eggs", "Apples"), content.uncheckedNames)
        assertEquals(screenOrder, content.uncheckedNames)
    }

    @Test
    fun `isLocked mirrors the row at snapshot time`() = runTest {
        seedList()

        assertFalse(reader.snapshot("list-1")!!.isLocked)
        dbRule.listDao.setLocked("list-1", true)
        assertTrue(reader.snapshot("list-1")!!.isLocked)
    }

    @Test
    fun `eligibility is true only with at least one picked and one unpicked`() = runTest {
        // 0 picked / 0 unpicked
        seedList("empty")
        assertFalse(eligibility.isPartlyShopped("empty"))

        // 1 picked / 0 unpicked
        seedList("allPicked")
        seedItem("p1", "Milk", picked = true, listId = "allPicked")
        assertFalse(eligibility.isPartlyShopped("allPicked"))

        // 0 picked / 1 unpicked
        seedList("nonePicked")
        seedItem("u1", "Milk", picked = false, listId = "nonePicked")
        assertFalse(eligibility.isPartlyShopped("nonePicked"))

        // 1 picked / 1 unpicked
        seedList("partly")
        seedItem("m1", "Milk", picked = true, listId = "partly")
        seedItem("m2", "Eggs", picked = false, listId = "partly")
        assertTrue(eligibility.isPartlyShopped("partly"))

        // a missing list is never eligible
        assertFalse(eligibility.isPartlyShopped("missing"))
    }
}
