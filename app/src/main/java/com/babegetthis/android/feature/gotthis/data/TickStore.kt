package com.babegetthis.android.feature.gotthis.data

import androidx.room.withTransaction
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.feature.gotthis.data.local.dao.PurchaseDao
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.TickOutboxEntity
import com.babegetthis.android.feature.gotthis.domain.GotThisIds
import com.babegetthis.android.feature.gotthis.domain.GotThisRules
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao

data class TickResult(
    val listId: String,
    val synced: Boolean,
    val ownerId: String?,
    val itemKey: String,
    val undone: Boolean,
    // True only when the call wrote a purchase: a real tick, not an untick or a repeated tap.
    val recorded: Boolean,
)

// The two transactions behind a tick and an untick: the item, its purchase and
// its outbox event change together or not at all, so a crash cannot leave them
// disagreeing.
class TickStore(
    private val db: AppDatabase,
    private val itemDao: ShoppingItemDao,
    private val listDao: ShoppingListDao,
    private val purchaseDao: PurchaseDao,
    private val clock: ReminderClock,
) {

    // Null when the item is gone.
    suspend fun tick(itemId: String): TickResult? = db.withTransaction {
        val item = itemDao.getItemRaw(itemId) ?: return@withTransaction null
        val ownerId = listDao.getListRaw(item.listId)?.accountId
        val synced = ownerId != null
        val name = item.name.take(GotThisRules.MAX_NAME_CHARS)
        val itemKey = ItemKey.of(name)
        // A repeated tap before the UI catches up must not make a second tick.
        if (item.isPickedUp) {
            return@withTransaction TickResult(item.listId, synced, ownerId, itemKey, undone = false, recorded = false)
        }
        val now = clock.now()
        itemDao.setPickedUp(itemId, isPickedUp = true, pickedUpAt = now, updatedAt = now, pendingSync = synced)

        val tickId = GotThisIds.tickId(itemId, now)
        val purchaseId = ownerId?.let { GotThisIds.purchaseId(it, tickId) } ?: GotThisIds.guestPurchaseId(tickId)
        purchaseDao.upsert(
            listOf(
                PurchaseEntity(
                    id = purchaseId,
                    tickId = tickId,
                    accountId = ownerId,
                    sourceItemId = itemId,
                    name = name,
                    itemKey = itemKey,
                    categoryId = item.categoryId,
                    boughtAt = now,
                    deletedAt = null,
                    pendingUpload = false,
                ),
            ),
        )
        if (ownerId != null) {
            purchaseDao.insertOutbox(
                TickOutboxEntity(
                    tickId = tickId,
                    accountId = ownerId,
                    itemId = itemId,
                    listId = item.listId,
                    tickedAt = now,
                    name = name,
                    categoryId = item.categoryId,
                    undoneAt = null,
                    revision = 0,
                ),
            )
        }
        TickResult(item.listId, synced, ownerId, itemKey, undone = false, recorded = true)
    }

    // Null when the item is gone. Undoes the tick's purchase only inside the
    // undo window; `pickedUpAt` is never cleared.
    suspend fun untick(itemId: String): TickResult? = db.withTransaction {
        val item = itemDao.getItemRaw(itemId) ?: return@withTransaction null
        val ownerId = listDao.getListRaw(item.listId)?.accountId
        val synced = ownerId != null
        val name = item.name.take(GotThisRules.MAX_NAME_CHARS)
        val itemKey = ItemKey.of(name)
        // Already unticked: a second untick must not move the tombstone or the revision.
        if (!item.isPickedUp) {
            return@withTransaction TickResult(item.listId, synced, ownerId, itemKey, undone = false, recorded = false)
        }
        val now = clock.now()
        itemDao.setPickedUp(
            itemId,
            isPickedUp = false,
            pickedUpAt = item.pickedUpAt,
            updatedAt = now,
            pendingSync = synced,
        )

        val pickedUpAt = item.pickedUpAt
        val undone = pickedUpAt != null && now - pickedUpAt in 0 until GotThisRules.UNDO_WINDOW_MILLIS
        if (undone) undo(item, name, requireNotNull(pickedUpAt), ownerId, now)
        TickResult(item.listId, synced, ownerId, itemKey, undone, recorded = false)
    }

    private suspend fun undo(item: ShoppingItemEntity, name: String, pickedUpAt: Long, ownerId: String?, now: Long) {
        val tickId = GotThisIds.tickId(item.id, pickedUpAt)
        if (ownerId == null) {
            purchaseDao.deleteByTickId(tickId)
            return
        }
        val purchase = purchaseDao.byTickId(tickId).firstOrNull()
        purchaseDao.tombstoneByTickId(tickId, now)
        val queued = purchaseDao.outboxByTickId(tickId)
        purchaseDao.insertOutbox(
            queued?.copy(undoneAt = now, revision = queued.revision + 1)
                ?: TickOutboxEntity(
                    tickId = tickId,
                    accountId = ownerId,
                    itemId = item.id,
                    listId = item.listId,
                    tickedAt = pickedUpAt,
                    name = purchase?.name ?: name,
                    categoryId = if (purchase != null) purchase.categoryId else item.categoryId,
                    undoneAt = now,
                    revision = 0,
                ),
        )
    }
}
