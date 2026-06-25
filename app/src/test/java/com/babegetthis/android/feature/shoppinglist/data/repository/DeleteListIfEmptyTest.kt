package com.babegetthis.android.feature.shoppinglist.data.repository

import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Covers deleteListIfEmpty(): deletes only when the list has no items.
class DeleteListIfEmptyTest {

    private val listDao = mockk<ShoppingListDao>(relaxed = true)
    private val itemDao = mockk<ShoppingItemDao>(relaxed = true)
    private val categoryDao = mockk<CategoryDao>(relaxed = true)
    private val repository = ShoppingListRepository(listDao, itemDao, categoryDao)

    @Test
    fun `deletes and returns true when the list is empty`() = runTest {
        coEvery { itemDao.getItemsByListIdOnce("list-1") } returns emptyList()

        val result = repository.deleteListIfEmpty("list-1")

        assertEquals(Result.Success(true), result)
        coVerify(exactly = 1) { listDao.deleteList("list-1") }
    }

    @Test
    fun `keeps and returns false when the list has items`() = runTest {
        coEvery { itemDao.getItemsByListIdOnce("list-1") } returns listOf(
            ShoppingItemEntity(
                id = "item-1",
                listId = "list-1",
                name = "Milk",
                quantity = "",
                createdAt = 0,
                updatedAt = 0,
            ),
        )

        val result = repository.deleteListIfEmpty("list-1")

        assertEquals(Result.Success(false), result)
        coVerify(exactly = 0) { listDao.deleteList(any()) }
    }
}
