package com.babegetthis.android.feature.gotthis.data

import androidx.room.withTransaction
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.sync.data.mapper.isoToMillis
import com.babegetthis.android.core.sync.data.mapper.millisToIso
import com.babegetthis.android.core.sync.data.repository.SyncPointStore
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.TickOutboxEntity
import com.babegetthis.android.feature.gotthis.data.local.watermark
import com.babegetthis.android.feature.gotthis.data.remote.GotThisRemote
import com.babegetthis.android.feature.gotthis.data.remote.OwnPurchaseRow
import com.babegetthis.android.feature.gotthis.data.remote.PurchaseRow
import com.babegetthis.android.feature.gotthis.data.remote.TickEventRow
import com.babegetthis.android.feature.gotthis.domain.GotThisIds
import com.babegetthis.android.feature.gotthis.domain.GotThisRules
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import kotlinx.coroutines.CancellationException

// The sync half of purchase history. Called by SyncEngine, which holds the sync
// lock, so nothing here takes one.
class GotThisSync(
    private val db: AppDatabase,
    private val remote: GotThisRemote,
    private val prefs: GotThisPrefs,
    private val syncPoints: SyncPointStore,
    private val clock: ReminderClock,
    private val backfill: GotThisBackfill,
) {

    private val purchaseDao = db.purchaseDao()
    private val stateDao = db.gotThisStateDao()
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

    // Switched off, nothing goes to the server: ticks and clears stay queued on
    // the phone and are sent once it is switched on again.
    suspend fun push(userId: String) {
        if (prefs.killed) return
        pushPendingClear(userId)
        pushOutbox(userId)
        pushOwnPurchases(userId)
    }

    // Switched off, push sends none of it, so going online could never clear a
    // sign-out warning about it (SyncEngine.hasUnsyncedChanges).
    suspend fun unsyncedCount(accountId: String): Int {
        if (prefs.killed) return 0
        return purchaseDao.countOutbox(accountId) +
            purchaseDao.countPendingUploads(accountId) +
            if (prefs.pendingClear(accountId) != null) 1 else 0
    }

    // Brings purchases down behind the account's watermark (I6): nothing at or
    // before the last applied reset or the pending clear is ever written, even
    // when the server has not heard of the clear yet.
    suspend fun pull(userId: String) {
        if (prefs.killed) return
        applyServerReset(userId)
        val fetched = remote.fetchPurchases(syncPoints.get(point(userId)))
        if (fetched.isEmpty()) return
        db.withTransaction {
            // Read here, not before the fetch: a Clear pressed while it was in
            // flight has already deleted its rows and must not get them back.
            val watermark = prefs.watermark(userId)
            val kept = fetched.filter { watermark == null || it.boughtAt.isoToMillis() > watermark }
            // A live server row must not undo an undo this phone has yet to send.
            val undoPending = kept.filter { it.deletedAt == null }
                .map { it.tickId }
                .chunked(GotThisRules.SQL_IN_CHUNK)
                .flatMap { purchaseDao.undoneOutboxTickIds(it) }
                .toSet()
            purchaseDao.upsert(
                kept.filter { it.deletedAt != null || it.tickId !in undoPending }.map { it.toEntity(userId) },
            )
        }
        // Trails the newest row by PULL_LAG_MILLIS, and never moves back.
        val next = fetched.maxOf { it.updatedAt.isoToMillis() } - GotThisRules.PULL_LAG_MILLIS
        val current = syncPoints.get(point(userId))?.isoToMillis()
        if (current == null || next > current) syncPoints.set(point(userId), next.millisToIso())
    }

    private suspend fun applyServerReset(userId: String) {
        val reset = remote.fetchResetAt()?.isoToMillis() ?: return
        applyReset(userId, reset)
    }

    // Brings the phone in line with a reset the server holds, whether pulled or
    // returned by a clear: a reset later than the one sent (another phone
    // cleared after this one) must still delete the purchases up to it.
    private suspend fun applyReset(userId: String, reset: Long) {
        val applied = prefs.lastAppliedReset(userId)
        if (applied != null && reset <= applied) return
        db.withTransaction {
            purchaseDao.deleteUpTo(userId, reset)
            stateDao.clearState(userId)
        }
        // Before the new reset is stored, so a crash in between redoes this
        // rather than leaving a point that skips rows.
        syncPoints.remove(point(userId))
        prefs.setLastAppliedReset(userId, reset)
    }

    // Runs under the sync lock before a claim: guest ticks that predate tick
    // times must become guest purchases first, or a pushed item without a tick
    // time makes the server write a second tick for a purchase that is also
    // uploaded.
    suspend fun prepareClaim() = backfill.backfillGuestTicks()

    // Sign-in from guest: the guest's history becomes the account's. Live rows
    // are sent up; the server has none of it.
    suspend fun claimGuestHistory(userId: String) {
        val others = db.withTransaction {
            val others = purchaseDao.otherAccountIds(userId)
            // Another account that lost its session leaves its history behind;
            // it is neither this person's nor sendable by them.
            purchaseDao.deleteOtherAccountPurchases(userId)
            purchaseDao.deleteOtherAccountOutbox(userId)
            stateDao.clearOtherAccountState(userId)
            val watermark = prefs.watermark(userId)
            val guest = purchaseDao.guestPurchases().filter { watermark == null || it.boughtAt > watermark }
            purchaseDao.deleteGuestPurchases()
            purchaseDao.upsert(
                guest.map {
                    it.copy(
                        id = GotThisIds.purchaseId(userId, it.tickId),
                        accountId = userId,
                        pendingUpload = it.deletedAt == null,
                    )
                },
            )
            stateDao.moveGuestState(userId)
            others
        }
        // Their rows are gone, so a later pull for them must start from the beginning.
        others.forEach { syncPoints.remove(point(it)) }
    }

    // Sign-out: the account's history leaves the phone with its lists. A pending
    // clear stays in prefs, to be sent when the account next syncs (R9). The
    // purchases sync points go with SyncEngine's clearing of every point.
    suspend fun evict() {
        db.withTransaction {
            purchaseDao.deleteAccountPurchases()
            purchaseDao.deleteAllOutbox()
            stateDao.clearAccountState()
        }
    }

    private fun PurchaseRow.toEntity(userId: String) = PurchaseEntity(
        id = id,
        tickId = tickId,
        accountId = userId,
        sourceItemId = sourceItemId,
        name = name,
        itemKey = ItemKey.of(name),
        categoryId = categoryId,
        boughtAt = boughtAt.isoToMillis(),
        deletedAt = deletedAt?.isoToMillis(),
        pendingUpload = false,
    )

    // Stays pending until the server answers; a failure must not hold back the
    // tick events behind it.
    private suspend fun pushPendingClear(userId: String) {
        val pending = prefs.pendingClear(userId) ?: return
        try {
            val resetAt = remote.clearHistory(pending.millisToIso()).isoToMillis()
            applyReset(userId, resetAt)
            // The server clamps the reset to its own clock. A phone clock ahead of
            // it kept rows between the two out of earlier pulls while moving the
            // point past them, and applyReset leaves the point when an earlier
            // reset already covers this one, so they must be fetched again.
            if (resetAt < pending) syncPoints.remove(point(userId))
            // A Clear pressed during the call replaced the pending time; it is not sent yet.
            if (prefs.pendingClear(userId) == pending) prefs.setPendingClear(userId, null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sent again on the next push.
        }
    }

    private suspend fun pushOutbox(userId: String) {
        // The server would reject them, and kept they hold the sign-out warning
        // up for good. Their purchases stay on the phone and go up as the
        // person's own, so their other phones and a reinstall see them.
        val cutoff = clock.now() - GotThisRules.PAST_ACCEPT_MILLIS
        purchaseDao.markPendingUploadForOutboxTickedBefore(userId, cutoff)
        purchaseDao.deleteOutboxTickedBefore(userId, cutoff)
        // Reads on from the last row sent in this push. Retry rows and rows
        // changed during the call stay in the table, so reading from the start
        // would send them again and never end.
        var afterTickedAt = Long.MIN_VALUE
        var afterTickId = ""
        while (true) {
            val batch = purchaseDao.outboxBatchAfter(userId, afterTickedAt, afterTickId, GotThisRules.PUSH_BATCH_SIZE)
            if (batch.isEmpty()) return
            afterTickedAt = batch.last().tickedAt
            afterTickId = batch.last().tickId
            val reply = remote.recordTicks(batch.map { it.toRow() })
            val finished = (reply.accepted + reply.rejected).toSet()
            val farFuture = clock.now() + GotThisRules.FUTURE_REJECT_MILLIS
            for (row in batch) {
                if (row.tickId !in finished) continue
                val deleted = purchaseDao.deleteOutboxIfRevision(row.tickId, row.revision) > 0
                if (!deleted || row.tickId !in reply.rejected) continue
                if (row.tickedAt > farFuture) {
                    purchaseDao.deleteByTickId(row.tickId)
                } else if (row.undoneAt == null) {
                    // Not a member, say, or outside the window: the phone keeps the
                    // purchase, so the server must still hear of it, as the
                    // person's own (pushOwnPurchases, right after this).
                    purchaseDao.markPendingUploadByTickIds(listOf(row.tickId))
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

    // One per account, so a pull never starts from another account's stamp.
    private fun point(userId: String) = "purchases:$userId"
}
