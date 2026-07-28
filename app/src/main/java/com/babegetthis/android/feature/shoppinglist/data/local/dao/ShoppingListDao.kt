package com.babegetthis.android.feature.shoppinglist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
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

    @Query("UPDATE shopping_lists SET isLocked = :locked WHERE id = :listId")
    suspend fun setLocked(listId: String, locked: Boolean)

    // Unlock every list — used when the device PIN is removed.
    @Query("UPDATE shopping_lists SET isLocked = 0 WHERE isLocked = 1")
    suspend fun unlockAll()

    // Counted in SQL. The repository used to collect the full
    // getAllListsWithItemCount() flow — a LEFT JOIN and GROUP BY over every list
    // and every item — and count in Kotlin, to produce one integer that Settings
    // asks for on every open.
    @Query("SELECT COUNT(*) FROM shopping_lists WHERE isLocked = 1")
    suspend fun lockedCount(): Int

    // Insert a list and its items as ONE transaction, so a failure or a
    // cancellation between the two writes can't leave a list stripped of its
    // items. Used by list-creation-with-items and by undo-restore; both used to
    // issue the two inserts independently.
    @Transaction
    suspend fun insertListWithItems(list: ShoppingListEntity, items: List<ShoppingItemEntity>) {
        insertList(list)
        if (items.isNotEmpty()) insertItemsForList(items)
    }

    // Lives here (rather than being called on ShoppingItemDao) purely so Room
    // runs it inside insertListWithItems' transaction.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemsForList(items: List<ShoppingItemEntity>)

    // CASCADE on shopping_items foreign key means items are auto-deleted too
    @Query("DELETE FROM shopping_lists WHERE id = :listId")
    suspend fun deleteList(listId: String)

    // Deletes the list ONLY if it has no items. The emptiness check and the
    // delete are one atomic SQL statement, so a concurrent item insert can
    // never sneak in between "count says 0" and "delete" (which would orphan
    // the new item via the CASCADE). Returns rows deleted: 1 = was empty and
    // deleted, 0 = had items (or didn't exist) and was kept.
    @Query("""
        DELETE FROM shopping_lists
        WHERE id = :listId
          AND NOT EXISTS (SELECT 1 FROM shopping_items WHERE listId = :listId)
    """)
    suspend fun deleteListIfEmpty(listId: String): Int
}

// Room can map query results to this class automatically.
// It matches column names to field names.
data class ShoppingListWithItemCount(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocked: Boolean,
    val itemCount: Int,
    val completedItemCount: Int,
)
