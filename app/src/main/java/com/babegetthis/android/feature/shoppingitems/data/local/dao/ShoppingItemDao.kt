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

    // Sorted by: shop (nulls last) → category (nulls last) → name
    // This groups items by store first, then by aisle within each store.
    @Query("""
        SELECT * FROM shopping_items
        WHERE listId = :listId
        ORDER BY
            CASE WHEN shop IS NULL THEN 1 ELSE 0 END,
            shop ASC,
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

    @Query("UPDATE shopping_items SET isPickedUp = :isPickedUp, updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun updatePickedUpStatus(itemId: String, isPickedUp: Boolean, updatedAt: Long)
}
