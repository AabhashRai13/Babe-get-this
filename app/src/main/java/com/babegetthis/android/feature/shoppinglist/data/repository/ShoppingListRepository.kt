package com.babegetthis.android.feature.shoppinglist.data.repository

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
) {
    fun getAllLists(): Flow<List<ShoppingList>> {
        return shoppingListDao.getAllListsWithItemCount().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getListById(listId: String): Flow<ShoppingList?> {
        return shoppingListDao.getListById(listId).map { it?.toDomain() }
    }

    suspend fun createList(name: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val list = ShoppingList(
            id = id,
            name = name,
            createdAt = now,
            updatedAt = now,
        )
        shoppingListDao.insertList(list.toEntity())
        return id
    }

    suspend fun updateListName(listId: String, newName: String) {
        val now = System.currentTimeMillis()
        val entity = shoppingListDao.getListById(listId).first() ?: return
        shoppingListDao.updateList(entity.copy(name = newName, updatedAt = now))
    }

    suspend fun deleteList(listId: String) {
        shoppingListDao.deleteList(listId)
    }
}
