package com.babegetthis.android.feature.shoppinglist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {

    // Returns all lists with their item counts and completion counts.
    // Both counts are calculated live by counting rows in shopping_items.
    // A list is "completed" when itemCount > 0 and completedItemCount == itemCount.
    @Query("""
        SELECT l.*,
               COUNT(i.id) AS itemCount,
               SUM(CASE WHEN i.isPickedUp = 1 THEN 1 ELSE 0 END) AS completedItemCount
        FROM shopping_lists l
        LEFT JOIN shopping_items i ON l.id = i.listId
        GROUP BY l.id
        ORDER BY l.createdAt DESC
    """)
    fun getAllListsWithItemCount(): Flow<List<ShoppingListWithItemCount>>

    @Query("SELECT * FROM shopping_lists WHERE id = :listId")
    fun getListById(listId: String): Flow<ShoppingListEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ShoppingListEntity)

    @Update
    suspend fun updateList(list: ShoppingListEntity)

    // CASCADE on shopping_items foreign key means items are auto-deleted too
    @Query("DELETE FROM shopping_lists WHERE id = :listId")
    suspend fun deleteList(listId: String)
}

// Room can map query results to this class automatically.
// It matches column names to field names.
data class ShoppingListWithItemCount(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val itemCount: Int,
    val completedItemCount: Int,
)
