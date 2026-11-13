package com.babegetthis.android.feature.gotthis.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.TickOutboxEntity
import kotlinx.coroutines.flow.Flow

data class KeyStatRow(val itemKey: String, val purchases: Int, val lastBoughtAt: Long)

// accountId is null for the guest; it matches with
// (accountId = :a OR (:a IS NULL AND accountId IS NULL)).
@Dao
interface PurchaseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<PurchaseEntity>)

    @Query("SELECT * FROM purchases WHERE tickId = :tickId")
    suspend fun byTickId(tickId: String): List<PurchaseEntity>

    @Query("UPDATE purchases SET deletedAt = :now WHERE tickId = :tickId")
    suspend fun tombstoneByTickId(tickId: String, now: Long)

    @Query("DELETE FROM purchases WHERE tickId = :tickId")
    suspend fun deleteByTickId(tickId: String)

    @Query(
        """
        SELECT itemKey, COUNT(*) AS purchases, MAX(boughtAt) AS lastBoughtAt FROM purchases
        WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL))
            AND deletedAt IS NULL
        GROUP BY itemKey
    """,
    )
    fun keyStats(accountId: String?): Flow<List<KeyStatRow>>

    @Query(
        """
        SELECT * FROM purchases
        WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL))
            AND deletedAt IS NULL AND itemKey IN (:keys)
    """,
    )
    suspend fun liveForKeys(accountId: String?, keys: List<String>): List<PurchaseEntity>

    @Query(
        """
        SELECT * FROM purchases
        WHERE (accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL))
            AND deletedAt IS NULL
    """,
    )
    suspend fun live(accountId: String?): List<PurchaseEntity>

    // Every row, for the key recompute.
    @Query("SELECT * FROM purchases")
    suspend fun all(): List<PurchaseEntity>

    @Query("DELETE FROM purchases WHERE accountId = :accountId AND boughtAt <= :boughtAt")
    suspend fun deleteUpTo(accountId: String, boughtAt: Long)

    @Query("DELETE FROM purchases WHERE accountId = :accountId OR (:accountId IS NULL AND accountId IS NULL)")
    suspend fun deleteAllFor(accountId: String?)

    @Query("SELECT * FROM purchases WHERE accountId IS NULL")
    suspend fun guestPurchases(): List<PurchaseEntity>

    @Query("DELETE FROM purchases WHERE accountId IS NULL")
    suspend fun deleteGuestPurchases()

    // Every other account with anything on the phone, for its sync point.
    @Query(
        """
        SELECT accountId FROM purchases WHERE accountId IS NOT NULL AND accountId != :userId
        UNION SELECT accountId FROM tick_outbox WHERE accountId != :userId
        UNION SELECT accountId FROM item_mutes WHERE accountId != '' AND accountId != :userId
        UNION SELECT accountId FROM hint_impressions WHERE accountId != '' AND accountId != :userId
        UNION SELECT accountId FROM reminder_followups WHERE accountId != '' AND accountId != :userId
    """,
    )
    suspend fun otherAccountIds(userId: String): List<String>

    // Another account signed in over a lost session: its rows can never be sent.
    @Query("DELETE FROM purchases WHERE accountId IS NOT NULL AND accountId != :userId")
    suspend fun deleteOtherAccountPurchases(userId: String)

    // Sign-out: everything an account owns. Guest rows stay.
    @Query("DELETE FROM purchases WHERE accountId IS NOT NULL")
    suspend fun deleteAccountPurchases()

    @Query(
        """
        SELECT * FROM purchases WHERE accountId = :accountId AND pendingUpload = 1 AND deletedAt IS NULL
        ORDER BY boughtAt, id LIMIT :limit
    """,
    )
    suspend fun pendingUploads(accountId: String, limit: Int): List<PurchaseEntity>

    @Query("UPDATE purchases SET pendingUpload = 0 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<String>)

    @Query("UPDATE purchases SET pendingUpload = 1 WHERE tickId IN (:tickIds)")
    suspend fun markPendingUploadByTickIds(tickIds: List<String>)

    // The live purchases of outbox events ticked before [before]: events the
    // server would no longer accept, whose purchases go up as the person's own.
    @Query(
        "UPDATE purchases SET pendingUpload = 1 WHERE deletedAt IS NULL AND tickId IN " +
            "(SELECT tickId FROM tick_outbox WHERE accountId = :accountId AND tickedAt < :before AND undoneAt IS NULL)",
    )
    suspend fun markPendingUploadForOutboxTickedBefore(accountId: String, before: Long)

    // Live rows only: an undone purchase must not be uploaded as live.
    @Query("SELECT COUNT(*) FROM purchases WHERE accountId = :accountId AND pendingUpload = 1 AND deletedAt IS NULL")
    suspend fun countPendingUploads(accountId: String): Int

    // ── Tick outbox ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(row: TickOutboxEntity)

    @Query("SELECT * FROM tick_outbox WHERE tickId = :tickId")
    suspend fun outboxByTickId(tickId: String): TickOutboxEntity?

    @Query("SELECT * FROM tick_outbox WHERE accountId = :accountId ORDER BY tickedAt, tickId LIMIT :limit")
    suspend fun outboxBatch(accountId: String, limit: Int): List<TickOutboxEntity>

    // Compare-and-delete: a change after the push read bumped the revision, so
    // the row stays for the next push. Returns the rows deleted.
    @Query("DELETE FROM tick_outbox WHERE tickId = :tickId AND revision = :revision")
    suspend fun deleteOutboxIfRevision(tickId: String, revision: Int): Int

    @Query("SELECT * FROM tick_outbox WHERE listId IN (:listIds)")
    suspend fun outboxForLists(listIds: List<String>): List<TickOutboxEntity>

    @Query("DELETE FROM tick_outbox WHERE listId IN (:listIds)")
    suspend fun deleteOutboxForLists(listIds: List<String>)

    @Query("DELETE FROM tick_outbox WHERE accountId = :accountId")
    suspend fun deleteOutboxFor(accountId: String)

    @Query("DELETE FROM tick_outbox WHERE accountId = :accountId AND tickedAt < :before")
    suspend fun deleteOutboxTickedBefore(accountId: String, before: Long)

    @Query("DELETE FROM tick_outbox WHERE accountId != :userId")
    suspend fun deleteOtherAccountOutbox(userId: String)

    @Query("DELETE FROM tick_outbox")
    suspend fun deleteAllOutbox()

    // Ticks whose undo is still waiting to be sent.
    @Query("SELECT tickId FROM tick_outbox WHERE undoneAt IS NOT NULL AND tickId IN (:tickIds)")
    suspend fun undoneOutboxTickIds(tickIds: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM tick_outbox WHERE accountId = :accountId")
    suspend fun countOutbox(accountId: String): Int
}
