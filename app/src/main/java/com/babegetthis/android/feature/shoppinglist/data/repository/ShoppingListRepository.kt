package com.babegetthis.android.feature.shoppinglist.data.repository

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.feature.shoppingitems.data.mapper.toEntity
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
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
    private val categoryDao: CategoryDao,
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

    // Voice-capture entry point: create a new list AND drop its items in one go.
    // Mirrors the (list insert + items insert) shape of restoreListWithItems below,
    // and per-item defaults follow ShoppingItemRepository.addItem so voice items
    // look identical to manually-typed ones. Returns the new list id so the
    // ViewModel can navigate into it.
    suspend fun createListWithItems(
        name: String,
        drafts: List<ItemDraft>,
    ): Result<String> = safeCall {
        val now = System.currentTimeMillis()
        val listId = UUID.randomUUID().toString()

        val list = ShoppingList(
            id = listId,
            name = name,
            createdAt = now,
            updatedAt = now,
        )
        shoppingListDao.insertList(list.toEntity())

        // Trust-boundary guard: the backend now returns draft.category as a real
        // category id ("cat-dairy-eggs"), matching our categories table. But never
        // trust the network blindly — keep the id only if it actually exists as a
        // row, otherwise fall back to null (uncategorized). first() reads the
        // current snapshot of the live categories Flow.
        val knownCategoryIds = categoryDao.getAllCategories().first().mapTo(HashSet()) { it.id }

        val itemEntities = drafts.map { draft ->
            ShoppingItem(
                id = UUID.randomUUID().toString(),
                listId = listId,
                name = draft.name,
                // Backend returns quantity + unit; the repository already flattened
                // them into draft.quantity. null → empty string to match the shape
                // of manually-typed items.
                quantity = draft.quantity.orEmpty(),
                // Use the backend's category id only if it's a known row; unknown
                // or null → uncategorized.
                categoryId = draft.category?.takeIf { it in knownCategoryIds },
                note = draft.note,
                shop = null,
                createdAt = now,
                updatedAt = now,
            ).toEntity()
        }
        if (itemEntities.isNotEmpty()) {
            shoppingItemDao.insertItems(itemEntities)
        }

        listId
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

    // Delete a list ONLY if it has no items. Used when the user leaves a list
    // detail screen they never added anything to (created a list, backed out).
    // We query the DB for the real item count rather than trusting a cached
    // value, so a list that actually has items is never deleted by mistake.
    // Returns true if the list was empty and got deleted.
    suspend fun deleteListIfEmpty(listId: String): Result<Boolean> = safeCall {
        val items = shoppingItemDao.getItemsByListIdOnce(listId)
        if (items.isEmpty()) {
            shoppingListDao.deleteList(listId)
            true
        } else {
            false
        }
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
