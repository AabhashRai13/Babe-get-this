package com.babegetthis.android.feature.shoppinglist.data.repository

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import com.babegetthis.android.feature.shoppinglist.data.mapper.toDomain
import com.babegetthis.android.feature.shoppinglist.data.mapper.toEntity
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingListRepository @Inject constructor(
    private val shoppingListDao: ShoppingListDao,
    private val shoppingItemDao: ShoppingItemDao,
) {
    // Flows don't need Result wrapping — Room handles errors internally
    // and the Flow just stops emitting. We wrap write operations only.
    fun getAllLists(): Flow<List<ShoppingList>> {
        return shoppingListDao.getAllListsWithItemCount().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getListById(listId: String): Flow<ShoppingList?> {
        return shoppingListDao.getListById(listId).map { it?.toDomain() }
    }

    suspend fun createList(name: String): Result<String> = safeCall {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val list = ShoppingList(
            id = id,
            name = name,
            createdAt = now,
            updatedAt = now,
        )
        shoppingListDao.insertList(list.toEntity())
        id
    }

    suspend fun updateListName(listId: String, newName: String): Result<Unit> = safeCall {
        val now = System.currentTimeMillis()
        val entity = shoppingListDao.getListById(listId).first()
            ?: throw IllegalStateException("List not found")
        shoppingListDao.updateList(entity.copy(name = newName, updatedAt = now))
    }

    // Captures items before deleting the list. The shopping_items foreign key
    // CASCADEs on list delete, so items are wiped from the DB — we return them
    // so the caller can hold them for undo and re-insert if needed.
    suspend fun deleteListAndCaptureItems(listId: String): Result<List<ShoppingItemEntity>> = safeCall {
        val items = shoppingItemDao.getItemsByListIdOnce(listId)
        shoppingListDao.deleteList(listId)
        items
    }

    // Re-insert a previously deleted list along with its items (for undo).
    // Restores both so derived fields like isCompleted recompute correctly.
    suspend fun restoreListWithItems(
        list: ShoppingList,
        items: List<ShoppingItemEntity>,
    ): Result<Unit> = safeCall {
        shoppingListDao.insertList(list.toEntity())
        if (items.isNotEmpty()) {
            shoppingItemDao.insertItems(items)
        }
    }
}
