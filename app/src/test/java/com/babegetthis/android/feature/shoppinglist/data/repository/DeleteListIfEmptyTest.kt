package com.babegetthis.android.feature.shoppinglist.data.repository

import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Covers deleteListIfEmpty(): the "only when empty" decision now lives in one
// atomic SQL statement in the DAO (so it can't race a concurrent insert), so
// this unit test covers the row-count → Boolean mapping. The SQL itself is
// exercised by instrumented tests / real usage.
class DeleteListIfEmptyTest {

    private val listDao = mockk<ShoppingListDao>(relaxed = true)
    private val itemDao = mockk<ShoppingItemDao>(relaxed = true)
    private val categoryDao = mockk<CategoryDao>(relaxed = true)
    private val repository = ShoppingListRepository(listDao, itemDao, categoryDao) {}

    @Test
    fun `returns true when the empty list was deleted`() = runTest {
        // Relaxed mockk would fabricate a non-null entity here; the shared-list
        // guard must see "local-only" (null) to reach the delete at all.
        coEvery { listDao.getListRaw("list-1") } returns null
        coEvery { listDao.deleteListIfEmpty("list-1") } returns 1

        val result = repository.deleteListIfEmpty("list-1")

        assertEquals(Result.Success(true), result)
    }

    @Test
    fun `returns false when the list had items and was kept`() = runTest {
        coEvery { listDao.getListRaw("list-1") } returns null
        coEvery { listDao.deleteListIfEmpty("list-1") } returns 0

        val result = repository.deleteListIfEmpty("list-1")

        assertEquals(Result.Success(false), result)
    }
}
