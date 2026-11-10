package com.babegetthis.android.feature.gotthis.data

import androidx.room.withTransaction
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.sync.data.mapper.isoToMillis
import com.babegetthis.android.core.sync.data.mapper.millisToIso
import com.babegetthis.android.core.sync.data.repository.SyncPointStore
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.model.TickOutboxEntity
import com.babegetthis.android.feature.gotthis.data.remote.GotThisRemote
import com.babegetthis.android.feature.gotthis.data.remote.OwnPurchaseRow
import com.babegetthis.android.feature.gotthis.data.remote.TickEventRow
import com.babegetthis.android.feature.gotthis.domain.GotThisRules
import kotlinx.coroutines.CancellationException

// The sync half of purchase history. Called by SyncEngine, which holds the sync
// lock, so nothing here takes one.
class GotThisSync(
    private val db: AppDatabase,
    private val remote: GotThisRemote,
    private val prefs: GotThisPrefs,
    private val syncPoints: SyncPointStore,
    private val clock: ReminderClock,
) {

    private val purchaseDao = db.purchaseDao()
    private val listDao = db.shoppingListDao()

    // A list deleted before it reached the server is about to be dropped, so
    // its live ticks can only reach the server as the person's own purchases
    // (D19). One transaction with the deletion, so a crash loses neither.
    suspend fun dropNeverSentLists() {
        db.withTransaction {
            val listIds = listDao.neverSentDeletedListIds()
            val rows = listIds.chunked(GotThisRules.SQL_IN_CHUNK).flatMap { purchaseDao.outboxForLists(it) }
            rows.filter { it.undoneAt == null }
                .map { it.tickId }
                .chunked(GotThisRules.SQL_IN_CHUNK)
                .forEach { purchaseDao.markPendingUploadByTickIds(it) }
            listIds.chunked(GotThisRules.SQL_IN_CHUNK).forEach { purchaseDao.deleteOutboxForLists(it) }
            listDao.deleteNeverSentDeletedLists()
        }
    }

    suspend fun push(userId: String) {
        pushPendingClear(userId)
        pushOutbox(userId)
        pushOwnPurchases(userId)
    }

    suspend fun unsyncedCount(accountId: String): Int = purchaseDao.countOutbox(accountId) +
        purchaseDao.countPendingUploads(accountId) +
        if (prefs.pendingClear(accountId) != null) 1 else 0

    // Stays pending until the server answers; a failure must not hold back the
    // tick events behind it.
    private suspend fun pushPendingClear(userId: String) {
        val pending = prefs.pendingClear(userId) ?: return
        try {
            val resetAt = remote.clearHistory(pending.millisToIso())
            prefs.setLastAppliedReset(userId, resetAt.isoToMillis())
            // A Clear pressed during the call replaced the pending time; it is not sent yet.
            if (prefs.pendingClear(userId) == pending) prefs.setPendingClear(userId, null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sent again on the next push.
        }
    }

    private suspend fun pushOutbox(userId: String) {
        // Rows sent in this push. Retry rows and rows changed during the call
        // stay in the table, so without this the loop would read them forever.
        val sent = mutableSetOf<String>()
        while (true) {
            val batch = purchaseDao.outboxBatch(userId, sent.size + GotThisRules.PUSH_BATCH_SIZE)
                .filter { it.tickId !in sent }
                .take(GotThisRules.PUSH_BATCH_SIZE)
            if (batch.isEmpty()) return
            sent += batch.map { it.tickId }
            val reply = remote.recordTicks(batch.map { it.toRow() })
            val finished = (reply.accepted + reply.rejected).toSet()
            val farFuture = clock.now() + GotThisRules.FUTURE_REJECT_MILLIS
            for (row in batch) {
                if (row.tickId !in finished) continue
                val deleted = purchaseDao.deleteOutboxIfRevision(row.tickId, row.revision) > 0
                if (deleted && row.tickId in reply.rejected && row.tickedAt > farFuture) {
                    purchaseDao.deleteByTickId(row.tickId)
                }
            }
        }
    }

    private suspend fun pushOwnPurchases(userId: String) {
        while (true) {
            val batch = purchaseDao.pendingUploads(userId, GotThisRules.PUSH_BATCH_SIZE)
            if (batch.isEmpty()) return
            remote.uploadOwnPurchases(
                batch.map {
                    OwnPurchaseRow(it.tickId, it.sourceItemId, it.name, it.categoryId, it.boughtAt.millisToIso())
                },
            )
            purchaseDao.markUploaded(batch.map { it.id })
        }
    }

    private fun TickOutboxEntity.toRow() = TickEventRow(
        id = tickId,
        itemId = itemId,
        listId = listId,
        tickedAt = tickedAt.millisToIso(),
        name = name,
        categoryId = categoryId,
        undoneAt = undoneAt?.millisToIso(),
    )
}
