package com.babegetthis.android.feature.shoppinglist.data.mapper

import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListWithItemCount
import com.babegetthis.android.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListMappersTest {

    private fun withCounts(
        itemCount: Int,
        completedItemCount: Int,
        isLocked: Boolean = false,
    ) = ShoppingListWithItemCount(
        id = "list-1",
        name = "Groceries",
        createdAt = TestData.T0,
        updatedAt = TestData.T0,
        isLocked = isLocked,
        itemCount = itemCount,
        completedItemCount = completedItemCount,
    )

    @Test
    fun `WithItemCount toDomain carries every field`() {
        val domain = withCounts(itemCount = 5, completedItemCount = 2, isLocked = true).toDomain()

        assertEquals("list-1", domain.id)
        assertEquals("Groceries", domain.name)
        assertEquals(TestData.T0, domain.createdAt)
        assertEquals(TestData.T0, domain.updatedAt)
        assertTrue(domain.isLocked)
        assertEquals(5, domain.itemCount)
        assertEquals(2, domain.completedItemCount)
    }

    @Test
    fun `list with all items picked up is completed`() {
        assertTrue(withCounts(itemCount = 3, completedItemCount = 3).toDomain().isCompleted)
    }

    @Test
    fun `list with some items picked up is not completed`() {
        assertFalse(withCounts(itemCount = 3, completedItemCount = 2).toDomain().isCompleted)
    }

    // An empty list is NOT completed — it's active and waiting for items.
    // Without the itemCount > 0 guard, 0 == 0 would mark every empty list done.
    @Test
    fun `empty list is not completed`() {
        assertFalse(withCounts(itemCount = 0, completedItemCount = 0).toDomain().isCompleted)
    }

    @Test
    fun `entity toDomain carries the stored fields`() {
        val domain = TestData.listEntity(isLocked = true).toDomain()

        assertEquals("list-1", domain.id)
        assertEquals("Groceries", domain.name)
        assertEquals(TestData.T0, domain.createdAt)
        assertTrue(domain.isLocked)
    }

    // Documented limitation, pinned so it can't drift silently: the entity has no
    // counts, so anything mapped this way always reports itemCount 0 and
    // therefore isCompleted false. Callers needing completion state must go
    // through ShoppingListWithItemCount.
    @Test
    fun `entity toDomain has no counts and is never completed`() {
        val domain = TestData.listEntity().toDomain()

        assertEquals(0, domain.itemCount)
        assertEquals(0, domain.completedItemCount)
        assertFalse(domain.isCompleted)
    }

    @Test
    fun `toEntity round-trips through toDomain`() {
        val original = TestData.listEntity(
            id = "list-9",
            name = "Hardware",
            createdAt = 1L,
            updatedAt = 2L,
            isLocked = true,
        )

        assertEquals(original, original.toDomain().toEntity())
    }

    @Test
    fun `toEntity drops the derived counts`() {
        val entity = TestData.list(itemCount = 4, completedItemCount = 4).toEntity()

        // Counts are query-derived, not stored — this is the intended lossy edge.
        assertEquals(TestData.listEntity(), entity)
    }
}
