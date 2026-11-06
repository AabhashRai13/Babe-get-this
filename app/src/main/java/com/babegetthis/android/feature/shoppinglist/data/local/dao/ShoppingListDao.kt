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

    // One owner's lists with their item counts and completion counts. Both
    // counts are calculated live by counting rows in shopping_items. A list is
    // "completed" when itemCount > 0 and completedItemCount == itemCount.
    // Tombstoned (deletedAt set) lists and items are invisible everywhere in the
    // UI; explicit columns instead of l.* so the sync columns never leak into
    // the projection.
    //
    // A device shows exactly one owner's lists (docs/technical-decisions/008):
    // the signed-in (or session-expired) account's, or — accountId null — the
    // guest lists. A shared replica with no owner (its session was lost before
    // lists had owners) is not a guest list: it is excluded until the account
    // claims it once the server confirms membership.
    @Query(
        """
        SELECT l.id, l.name, l.createdAt, l.updatedAt, l.isLocked,
               COUNT(i.id) AS itemCount,
               SUM(CASE WHEN i.isPickedUp = 1 THEN 1 ELSE 0 END) AS completedItemCount
        FROM shopping_lists l
        LEFT JOIN shopping_items i ON l.id = i.listId AND i.deletedAt IS NULL
        WHERE l.deletedAt IS NULL
          AND ((:accountId IS NULL AND l.accountId IS NULL AND l.shareCode IS NULL)
               OR l.accountId = :accountId)
        GROUP BY l.id
        ORDER BY l.createdAt DESC
    """,
    )
    fun getListsWithItemCount(accountId: String?): Flow<List<ShoppingListWithItemCount>>

    @Query("SELECT * FROM shopping_lists WHERE id = :listId AND deletedAt IS NULL")
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
    // list-with-counts flow — a LEFT JOIN and GROUP BY over every list
    // and every item — and count in Kotlin, to produce one integer that Settings
    // asks for on every open.
    @Query("SELECT COUNT(*) FROM shopping_lists WHERE isLocked = 1")
    suspend fun lockedCount(): Int

    @Query("SELECT id FROM shopping_lists WHERE isLocked = 1")
    suspend fun getLockedListIds(): List<String>

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
    @Query(
        """
        DELETE FROM shopping_lists
        WHERE id = :listId
          AND NOT EXISTS (SELECT 1 FROM shopping_items WHERE listId = :listId)
    """,
    )
    suspend fun deleteListIfEmpty(listId: String): Int

    // ── Sync primitives (account lists only; repositories route here when the
    //    list has an owner) ─────────────────────────────────────────────────

    // Tombstone variant of deleteListIfEmpty for account lists: the deletion has
    // to reach the account's other phones. Same single-statement guard, counting
    // live items only — a tombstoned item is already gone for the user.
    @Query(
        """
        UPDATE shopping_lists
        SET deletedAt = :now, updatedAt = :now, pendingSync = 1
        WHERE id = :listId
          AND deletedAt IS NULL
          AND NOT EXISTS (SELECT 1 FROM shopping_items WHERE listId = :listId AND deletedAt IS NULL)
    """,
    )
    suspend fun softDeleteListIfEmpty(listId: String, now: Long): Int

    // Soft delete: the tombstone must survive locally so the deletion itself
    // can sync. deletedAt doubles as the row's last-modified time.
    @Query(
        """
        UPDATE shopping_lists
        SET deletedAt = :now, updatedAt = :now, pendingSync = 1
        WHERE id = :listId
    """,
    )
    suspend fun softDeleteList(listId: String, now: Long)

    // One account's rows not yet pushed — includes tombstoned rows on purpose.
    // Scoped to the account so another account's leftovers (possible after a
    // lost session) never ride along under the wrong session.
    @Query("SELECT * FROM shopping_lists WHERE pendingSync = 1 AND accountId = :accountId")
    suspend fun getPendingAccountLists(accountId: String): List<ShoppingListEntity>

    // What the push above would send, counted — the sign-out warning asks on
    // every sign-out, and a row push can never send must not keep it showing.
    @Query("SELECT COUNT(*) FROM shopping_lists WHERE pendingSync = 1 AND accountId = :accountId")
    suspend fun countPendingAccountLists(accountId: String): Int

    // Lists deleted before they ever reached the server: nobody else can have
    // them, so there is no tombstone to deliver and the row can simply go
    // (CASCADE takes the items). Called by the push, which is the one place
    // serialised with the INSERT such a row might otherwise have been racing.
    @Query("DELETE FROM shopping_lists WHERE onServer = 0 AND deletedAt IS NOT NULL")
    suspend fun deleteNeverSentDeletedLists()

    // Sharing a list that already syncs: the server set the code, record it.
    @Query("UPDATE shopping_lists SET shareCode = :code WHERE id = :id")
    suspend fun setShareCode(id: String, code: String)

    // The first INSERT landed; every later push of this row is an upsert.
    @Query("UPDATE shopping_lists SET onServer = 1 WHERE id = :id")
    suspend fun markListOnServer(id: String)

    // Guarded clear: only if the row wasn't edited again mid-push (updatedAt
    // still matches the pushed snapshot). Otherwise the newer edit would be
    // silently stranded with its flag wiped.
    @Query("UPDATE shopping_lists SET pendingSync = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markListSynced(id: String, updatedAt: Long)

    // Sync-internal read: NO tombstone filter — LWW must see deleted rows too.
    @Query("SELECT * FROM shopping_lists WHERE id = :listId")
    suspend fun getListRaw(listId: String): ShoppingListEntity?

    // The account's live lists — what account-wide catch-up already knows.
    @Query("SELECT id FROM shopping_lists WHERE accountId = :accountId AND deletedAt IS NULL")
    suspend fun getAccountListIds(accountId: String): List<String>

    // Give an ownerless replica to the account the server just vouched for.
    @Query("UPDATE shopping_lists SET accountId = :accountId, onServer = 1 WHERE id = :id AND accountId IS NULL")
    suspend fun claimList(id: String, accountId: String)

    // Telemetry-only read — see ShoppingItemDao.countPendingSync for why this
    // is a COUNT rather than the size of getPendingAccountLists().
    @Query("SELECT COUNT(*) FROM shopping_lists WHERE pendingSync = 1")
    suspend fun countPendingSync(): Int

    // ── Claim on sign-in (docs/technical-decisions/008) ────────────────────

    @Query("SELECT id FROM shopping_lists WHERE accountId IS NOT NULL AND accountId != :userId")
    suspend fun getOtherAccountListIds(userId: String): List<String>

    @Query("DELETE FROM shopping_lists WHERE accountId IS NOT NULL AND accountId != :userId")
    suspend fun deleteOtherAccountLists(userId: String)

    // Must run BEFORE claimGuestLists: afterwards the subquery matches nothing.
    @Query(
        """
        UPDATE shopping_items SET pendingSync = 1
        WHERE deletedAt IS NULL
          AND listId IN (SELECT id FROM shopping_lists WHERE accountId IS NULL AND shareCode IS NULL)
    """,
    )
    suspend fun markGuestListItemsPending()

    @Query(
        "UPDATE shopping_lists SET accountId = :userId, pendingSync = 1 WHERE accountId IS NULL AND shareCode IS NULL",
    )
    suspend fun claimGuestLists(userId: String): Int

    @Query("UPDATE shopping_lists SET accountId = :userId WHERE accountId IS NULL AND shareCode IS NOT NULL")
    suspend fun claimOwnerlessSharedLists(userId: String)

    @Query("SELECT id FROM shopping_lists WHERE accountId IS NULL AND shareCode IS NOT NULL")
    suspend fun getOwnerlessSharedListIds(): List<String>

    // Undo a first-run claim the server didn't confirm: back to ownerless, so
    // it is never pushed, shown, or kept past the next sign-out.
    @Query("UPDATE shopping_lists SET accountId = NULL WHERE id IN (:ids) AND accountId = :userId")
    suspend fun releaseClaims(ids: List<String>, userId: String)

    // Everything a sign-in does to the device's lists, as one transaction so a
    // crash can't leave half the guest lists claimed and their items unqueued.
    // Returns how many guest lists moved into the account. Another account's
    // replicas are dropped (the server holds them); guest lists and their live
    // items are queued for upload; ownerless shared replicas are taken only when
    // the caller knows they predate ownership (the first claim after the update).
    @Transaction
    suspend fun claimDeviceLists(userId: String, includeOwnerlessShared: Boolean): Int {
        deleteOtherAccountLists(userId)
        markGuestListItemsPending()
        val claimed = claimGuestLists(userId)
        if (includeOwnerlessShared) claimOwnerlessSharedLists(userId)
        return claimed
    }

    // Account-list eviction on explicit sign-out (docs/technical-decisions/008).
    // Hard delete on purpose — eviction, not a deletion to sync; CASCADE clears
    // the items. Ownerless shared replicas go too; guest lists are untouched.
    @Query("DELETE FROM shopping_lists WHERE accountId IS NOT NULL OR shareCode IS NOT NULL")
    suspend fun deleteAccountLists()
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
