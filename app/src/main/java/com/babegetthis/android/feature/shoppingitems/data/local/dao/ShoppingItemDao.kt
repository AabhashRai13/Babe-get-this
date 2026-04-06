package com.babegetthis.android.feature.shoppingitems.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {

    // Gets items for a list, sorted by category (nulls last) then by name.
    // This groups items by category automatically in the UI.
    @Query("""
        SELECT * FROM shopping_items
        WHERE listId = :listId
        ORDER BY
            CASE WHEN categoryId IS NULL THEN 1 ELSE 0 END,
            categoryId ASC,
            name ASC
    """)
    fun getItemsByListId(listId: String): Flow<List<ShoppingItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItemEntity)

    @Update
    suspend fun updateItem(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    // Toggle picked up status — used when tapping an item in the store
    @Query("UPDATE shopping_items SET isPickedUp = :isPickedUp, updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun updatePickedUpStatus(itemId: String, isPickedUp: Boolean, updatedAt: Long)
}
