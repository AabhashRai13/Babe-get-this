package com.babegetthis.android.feature.gotthis.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.babegetthis.android.feature.gotthis.data.local.model.HintImpressionEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ReminderFollowUpEntity
import kotlinx.coroutines.flow.Flow

// Small per-phone state, never synced. Guests use "" as accountId.
@Dao
interface GotThisStateDao {

    @Query("SELECT * FROM item_mutes WHERE accountId = :accountId")
    suspend fun mutes(accountId: String): List<ItemMuteEntity>

    @Query("SELECT * FROM item_mutes WHERE accountId = :accountId")
    fun observeMutes(accountId: String): Flow<List<ItemMuteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMute(row: ItemMuteEntity)

    @Query("SELECT * FROM hint_impressions WHERE itemId = :itemId")
    suspend fun impression(itemId: String): HintImpressionEntity?

    // Returns -1 when the item already has a row.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImpressionIfAbsent(row: HintImpressionEntity): Long

    @Query("DELETE FROM hint_impressions WHERE itemId = :itemId")
    suspend fun deleteImpression(itemId: String)

    @Query("DELETE FROM hint_impressions WHERE shownAt < :olderThan")
    suspend fun pruneImpressions(olderThan: Long)

    @Query("SELECT * FROM reminder_followups WHERE accountId = :accountId")
    suspend fun followUps(accountId: String): List<ReminderFollowUpEntity>

    // A new notification never overwrites an unscored row.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFollowUp(row: ReminderFollowUpEntity)

    @Query("DELETE FROM reminder_followups WHERE accountId = :accountId AND isoWeek = :isoWeek")
    suspend fun deleteFollowUp(accountId: String, isoWeek: String)

    @Query("DELETE FROM item_mutes WHERE accountId = :accountId")
    suspend fun deleteMutes(accountId: String)

    @Query("DELETE FROM hint_impressions WHERE accountId = :accountId")
    suspend fun deleteImpressions(accountId: String)

    @Query("DELETE FROM reminder_followups WHERE accountId = :accountId")
    suspend fun deleteFollowUps(accountId: String)

    // Sign-out: every account's state, the guest's ("") stays.
    @Query("DELETE FROM item_mutes WHERE accountId != ''")
    suspend fun deleteAccountMutes()

    @Query("DELETE FROM hint_impressions WHERE accountId != ''")
    suspend fun deleteAccountImpressions()

    @Query("DELETE FROM reminder_followups WHERE accountId != ''")
    suspend fun deleteAccountFollowUps()

    @Transaction
    suspend fun clearAccountState() {
        deleteAccountMutes()
        deleteAccountImpressions()
        deleteAccountFollowUps()
    }

    // Another account signed in over a lost session; the guest's ("") stays.
    @Query("DELETE FROM item_mutes WHERE accountId != '' AND accountId != :userId")
    suspend fun deleteOtherMutes(userId: String)

    @Query("DELETE FROM hint_impressions WHERE accountId != '' AND accountId != :userId")
    suspend fun deleteOtherImpressions(userId: String)

    @Query("DELETE FROM reminder_followups WHERE accountId != '' AND accountId != :userId")
    suspend fun deleteOtherFollowUps(userId: String)

    @Transaction
    suspend fun clearOtherAccountState(userId: String) {
        deleteOtherMutes(userId)
        deleteOtherImpressions(userId)
        deleteOtherFollowUps(userId)
    }

    // Key recompute: the state was learned from keys that no longer mean the same.
    @Query("DELETE FROM item_mutes")
    suspend fun deleteAllMutes()

    @Query("DELETE FROM hint_impressions")
    suspend fun deleteAllImpressions()

    @Query("DELETE FROM reminder_followups")
    suspend fun deleteAllFollowUps()

    @Transaction
    suspend fun clearAllState() {
        deleteAllMutes()
        deleteAllImpressions()
        deleteAllFollowUps()
    }

    @Transaction
    suspend fun clearState(accountId: String) {
        deleteMutes(accountId)
        deleteImpressions(accountId)
        deleteFollowUps(accountId)
    }

    // OR IGNORE: where the account already has the same key, its row stays and
    // the guest's is dropped by the clearState of "" below.
    @Query("UPDATE OR IGNORE item_mutes SET accountId = :toAccountId WHERE accountId = ''")
    suspend fun moveGuestMutes(toAccountId: String)

    @Query("UPDATE hint_impressions SET accountId = :toAccountId WHERE accountId = ''")
    suspend fun moveGuestImpressions(toAccountId: String)

    @Query("UPDATE OR IGNORE reminder_followups SET accountId = :toAccountId WHERE accountId = ''")
    suspend fun moveGuestFollowUps(toAccountId: String)

    @Transaction
    suspend fun moveGuestState(toAccountId: String) {
        moveGuestMutes(toAccountId)
        moveGuestImpressions(toAccountId)
        moveGuestFollowUps(toAccountId)
        clearState("")
    }
}
