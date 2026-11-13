package com.babegetthis.android.feature.gotthis.data

import androidx.room.withTransaction
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.domain.GotThisIds
import com.babegetthis.android.feature.gotthis.domain.GotThisRules
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Start-up housekeeping for purchase history, off the main thread. Account
// lists are backfilled on the server and arrive with the next pull.
class GotThisBackfill(private val db: AppDatabase, private val prefs: GotThisPrefs, private val clock: ReminderClock) {

    private val itemDao = db.shoppingItemDao()
    private val listDao = db.shoppingListDao()
    private val purchaseDao = db.purchaseDao()
    private val stateDao = db.gotThisStateDao()

    // Held with the done flag, so the start-up run and a claim-time call
    // neither interleave nor both do the work.
    private val guestMutex = Mutex()

    suspend fun runOnce() {
        backfillGuestTicks()
        if (prefs.keyVersion != ItemKey.VERSION) recomputeKeys()
        stateDao.pruneImpressions(clock.now() - GotThisRules.IMPRESSION_PRUNE_MILLIS)
    }

    suspend fun backfillGuestTicks() {
        guestMutex.withLock {
            if (prefs.backfillDone) return
            listDao.getGuestListIds().forEach { backfillList(it) }
            prefs.backfillDone = true
        }
    }

    // Items ticked before this feature have no tick time; their last change is
    // the best one there is.
    private suspend fun backfillList(listId: String) {
        db.withTransaction {
            val purchases = itemDao.tickedWithoutTime(listId).map { item ->
                itemDao.setPickedUpAt(item.id, item.updatedAt)
                val tickId = GotThisIds.tickId(item.id, item.updatedAt)
                val name = item.name.take(GotThisRules.MAX_NAME_CHARS)
                PurchaseEntity(
                    id = GotThisIds.guestPurchaseId(tickId),
                    tickId = tickId,
                    accountId = null,
                    sourceItemId = item.id,
                    name = name,
                    itemKey = ItemKey.of(name),
                    categoryId = item.categoryId,
                    boughtAt = item.updatedAt,
                    deletedAt = null,
                    pendingUpload = false,
                )
            }
            purchaseDao.upsert(purchases)
        }
    }

    // The mutes, impressions and follow-ups were keyed by the old algorithm and
    // mean nothing under the new one.
    private suspend fun recomputeKeys() {
        db.withTransaction {
            purchaseDao.upsert(purchaseDao.all().map { it.copy(itemKey = ItemKey.of(it.name)) })
            stateDao.clearAllState()
        }
        prefs.keyVersion = ItemKey.VERSION
    }
}
