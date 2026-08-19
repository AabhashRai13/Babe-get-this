package com.babegetthis.android.feature.shoppingitems.data.repository

import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.AppErrorException
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import com.babegetthis.android.core.sync.SyncKicker
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import com.babegetthis.android.feature.shoppingitems.data.mapper.toDomain
import com.babegetthis.android.feature.shoppingitems.data.mapper.toEntity
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingItemRepository @Inject constructor(
    private val shoppingItemDao: ShoppingItemDao,
    private val categoryDao: CategoryDao,
    private val shoppingListDao: ShoppingListDao,
    private val syncKicker: SyncKicker,
) {

    // One raw lookup decides every write's sync behavior: items of shared
    // lists are marked pendingSync and kick a push; local-only items behave
    // exactly as before this feature existed.
    private suspend fun isShared(listId: String): Boolean =
        shoppingListDao.getListRaw(listId)?.shareCode != null

    fun getItemsByListId(listId: String): Flow<List<ShoppingItem>> {
        return combine(
            shoppingItemDao.getItemsByListId(listId),
            categoryDao.getAllCategories()
        ) { items, categories ->
            val categoryMap = categories.associate { it.id to it.name }

            items.map { entity ->
                entity.toDomain(categoryName = entity.categoryId?.let { categoryMap[it] })
            }
        }
    }

    suspend fun addItem(
        listId: String,
        name: String,
        quantity: String,
        categoryId: String? = null,
        shop: String? = null,
        note: String? = null,
    ): Result<String> = safeCall {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val item = ShoppingItem(
            id = id,
            listId = listId,
            // Validated here, not only in the dialog: this is the trust boundary,
            // and voice-transcribed drafts reach the same table without ever
            // passing through a dialog. A blank name renders as an empty row.
            name = name.requireItemName(),
            quantity = quantity,
            categoryId = categoryId,
            note = note,
            shop = shop,
            createdAt = now,
            updatedAt = now,
        )
        val shared = isShared(listId)
        shoppingItemDao.insertItem(item.toEntity().copy(pendingSync = shared))
        if (shared) syncKicker.pushSoon()
        id
    }

    suspend fun updateItem(item: ShoppingItem): Result<Unit> = safeCall {
        val updatedItem = item.copy(updatedAt = System.currentTimeMillis())
        val shared = isShared(item.listId)
        shoppingItemDao.updateItem(updatedItem.toEntity().copy(pendingSync = shared))
        if (shared) syncKicker.pushSoon()
    }

    suspend fun togglePickedUp(itemId: String, isPickedUp: Boolean): Result<Unit> = safeCall {
        val listId = shoppingItemDao.getItemRaw(itemId)?.listId
        val shared = listId != null && isShared(listId)
        shoppingItemDao.updatePickedUpStatus(
            itemId = itemId,
            isPickedUp = isPickedUp,
            updatedAt = System.currentTimeMillis(),
            pendingSync = shared,
        )
        if (shared) syncKicker.pushSoon()
    }

    suspend fun deleteItem(itemId: String): Result<Unit> = safeCall {
        val listId = shoppingItemDao.getItemRaw(itemId)?.listId
        if (listId != null && isShared(listId)) {
            // Tombstone, not DELETE: the deletion itself must sync.
            shoppingItemDao.softDeleteItem(itemId, now = System.currentTimeMillis())
            syncKicker.pushSoon()
        } else {
            shoppingItemDao.deleteItem(itemId)
        }
    }

    // Re-insert a previously deleted item with its original ID (for undo).
    // For shared items the row is tombstoned, not gone — the REPLACE insert
    // clears deletedAt, and the dirty flag pushes the revival to members
    // (explicit-null serialization carries deleted_at = null to the server).
    suspend fun restoreItem(item: ShoppingItem): Result<Unit> = safeCall {
        val shared = isShared(item.listId)
        shoppingItemDao.insertItem(item.toEntity().copy(pendingSync = shared))
        if (shared) syncKicker.pushSoon()
    }

    // Mirrors ShoppingListRepository.requireListName — trims, and rejects blank
    // through safeCall as a ValidationError rather than storing an empty row.
    private fun String.requireItemName(): String {
        val trimmed = trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.ValidationError("Item name can't be empty."))
        }
        return trimmed
    }
}
