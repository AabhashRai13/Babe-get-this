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
    @Query(
        """
        SELECT * FROM shopping_items
        WHERE listId = :listId AND deletedAt IS NULL
        ORDER BY
            CASE WHEN shop IS NULL THEN 1 ELSE 0 END,
            shop ASC,
            CASE WHEN categoryId IS NULL THEN 1 ELSE 0 END,
            categoryId ASC,
            name ASC
    """,
    )
    fun getItemsByListId(listId: String): Flow<List<ShoppingItemEntity>>

    // One-shot read for snapshotting items before a CASCADE delete.
    // Unlike getItemsByListId (Flow), this returns once and completes — used
    // by the undo-delete flow so we can re-insert items if the user undoes.
    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND deletedAt IS NULL")
    suspend fun getItemsByListIdOnce(listId: String): List<ShoppingItemEntity>

    // One-shot read in exactly getItemsByListId's order (keep the two ORDER BYs
    // identical), for the shopping reminder: the names it shows must come in
    // the order the items screen lists them.
    @Query(
        """
        SELECT * FROM shopping_items
        WHERE listId = :listId AND deletedAt IS NULL
        ORDER BY
            CASE WHEN shop IS NULL THEN 1 ELSE 0 END,
            shop ASC,
            CASE WHEN categoryId IS NULL THEN 1 ELSE 0 END,
            categoryId ASC,
            name ASC
    """,
    )
    suspend fun getItemsByListIdOnceOrdered(listId: String): List<ShoppingItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Update
    suspend fun updateItem(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query(
        """
        UPDATE shopping_items
        SET isPickedUp = :isPickedUp, updatedAt = :updatedAt, pendingSync = :pendingSync
        WHERE id = :itemId
    """,
    )
    suspend fun updatePickedUpStatus(itemId: String, isPickedUp: Boolean, updatedAt: Long, pendingSync: Boolean)

    // A tick or untick: also records the tick time (kept on untick, so the
    // caller passes the existing time then).
    @Query(
        """
        UPDATE shopping_items
        SET isPickedUp = :isPickedUp, pickedUpAt = :pickedUpAt, updatedAt = :updatedAt, pendingSync = :pendingSync
        WHERE id = :itemId
    """,
    )
    suspend fun setPickedUp(
        itemId: String,
        isPickedUp: Boolean,
        pickedUpAt: Long?,
        updatedAt: Long,
        pendingSync: Boolean,
    )

    // Names of one owner's live, unticked items (null is the guest's lists).
    @Query(
        """
        SELECT i.name FROM shopping_items i
        JOIN shopping_lists l ON l.id = i.listId
        WHERE i.isPickedUp = 0 AND i.deletedAt IS NULL AND l.deletedAt IS NULL
            AND (l.accountId = :accountId OR (:accountId IS NULL AND l.accountId IS NULL))
    """,
    )
    fun uncheckedNames(accountId: String?): Flow<List<String>>

    // ── Sync primitives (shared lists only) — see ShoppingListDao ───────────

    @Query(
        """
        UPDATE shopping_items
        SET deletedAt = :now, updatedAt = :now, pendingSync = 1
        WHERE id = :itemId
    """,
    )
    suspend fun softDeleteItem(itemId: String, now: Long)

    // The push's read: one account's pending items — includes tombstoned rows
    // on purpose. The upsert is one batch, so a single row the server rejects
    // (another account's, after a lost session) would otherwise fail every
    // push, forever. There is deliberately no unscoped variant.
    @Query(
        """
        SELECT i.* FROM shopping_items i
        JOIN shopping_lists l ON l.id = i.listId
        WHERE i.pendingSync = 1 AND l.accountId = :accountId
    """,
    )
    suspend fun getPendingSyncItemsForAccount(accountId: String): List<ShoppingItemEntity>

    // The same rows, counted — see ShoppingListDao.countPendingAccountLists.
    @Query(
        """
        SELECT COUNT(*) FROM shopping_items i
        JOIN shopping_lists l ON l.id = i.listId
        WHERE i.pendingSync = 1 AND l.accountId = :accountId
    """,
    )
    suspend fun countPendingSyncItemsForAccount(accountId: String): Int

    // Guarded clear — see ShoppingListDao.markListSynced.
    @Query("UPDATE shopping_items SET pendingSync = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markItemSynced(id: String, updatedAt: Long)

    // Sync-internal read: NO tombstone filter — LWW must see deleted rows too.
    @Query("SELECT * FROM shopping_items WHERE id = :itemId")
    suspend fun getItemRaw(itemId: String): ShoppingItemEntity?

    // Telemetry-only reads (see core/telemetry/TelemetryContext). COUNTs rather
    // than materialising every row to throw them all away — this runs on
    // screen changes, so it stays cheap by construction rather than by luck.
    @Query("SELECT COUNT(*) FROM shopping_items WHERE deletedAt IS NULL")
    suspend fun countActiveItems(): Int

    @Query("SELECT COUNT(*) FROM shopping_items WHERE pendingSync = 1")
    suspend fun countPendingSync(): Int
}
