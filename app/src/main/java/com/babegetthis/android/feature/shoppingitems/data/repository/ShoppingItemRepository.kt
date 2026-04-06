package com.babegetthis.android.feature.shoppingitems.data.repository

import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
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
) {
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
    ): Result<String> = safeCall {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val item = ShoppingItem(
            id = id,
            listId = listId,
            name = name,
            quantity = quantity,
            categoryId = categoryId,
            shop = shop,
            createdAt = now,
            updatedAt = now,
        )
        shoppingItemDao.insertItem(item.toEntity())
        id
    }

    suspend fun updateItem(item: ShoppingItem): Result<Unit> = safeCall {
        val updatedItem = item.copy(updatedAt = System.currentTimeMillis())
        shoppingItemDao.updateItem(updatedItem.toEntity())
    }

    suspend fun togglePickedUp(itemId: String, isPickedUp: Boolean): Result<Unit> = safeCall {
        shoppingItemDao.updatePickedUpStatus(
            itemId = itemId,
            isPickedUp = isPickedUp,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun deleteItem(itemId: String): Result<Unit> = safeCall {
        shoppingItemDao.deleteItem(itemId)
    }
}
