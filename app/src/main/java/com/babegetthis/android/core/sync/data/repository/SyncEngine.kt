package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import com.babegetthis.android.core.sync.data.mapper.isoToMillis
import com.babegetthis.android.core.sync.data.mapper.toEntity
import com.babegetthis.android.core.sync.data.mapper.toRow
import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.core.sync.data.remote.SharedListRemote
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao

// The sync side-process: pushes dirty Room rows up, applies server rows down.
// Nothing in the UI layer knows this class exists — remote changes land as
// ordinary Room writes and the existing Flows redraw.
//
// Conflict model is "last write to REACH THE SERVER wins": the server stamps
// updated_at on every write, incoming rows carry that stamp into Room's
// updatedAt, and a dirty local row always beats incoming data locally because
// its eventual push will out-timestamp whatever it beat. No phone clock is
// ever compared against a server clock to decide a winner.
class SyncEngine(
    private val listDao: ShoppingListDao,
    private val itemDao: ShoppingItemDao,
    private val remote: SharedListRemote,
    private val syncPoints: SyncPointStore,
    private val currentUserId: () -> String?,
) {

    // Upsert everything pendingSync. Lists strictly before items: the server's
    // creator-membership trigger on `lists` is what makes the item RLS checks
    // pass (see the SQL migration's client contract note). Signed out → no-op;
    // the queue just waits.
    suspend fun push(): Result<Unit> = safeCall {
        val userId = currentUserId()
        if (userId != null) {
            val lists = listDao.getPendingSyncLists().filter { it.shareCode != null }
            if (lists.isNotEmpty()) {
                remote.upsertLists(lists.map { it.toRow(createdBy = userId) })
                // Guarded per-row clear: a row edited again mid-push keeps its
                // flag (updatedAt no longer matches) and re-pushes next kick.
                lists.forEach { listDao.markListSynced(it.id, it.updatedAt) }
            }
            val items = itemDao.getPendingSyncItems()
            if (items.isNotEmpty()) {
                remote.upsertItems(items.map { it.toRow() })
                items.forEach { itemDao.markItemSynced(it.id, it.updatedAt) }
            }
        }
    }

    // Fetch everything changed since the last sync point and land it in Room.
    // This is the ONE apply path: realtime events don't carry data anywhere —
    // they just trigger this. Idempotent by construction, so gte-refetch of
    // the newest known row is harmless.
    suspend fun catchUp(listId: String): Result<Unit> = safeCall {
        val since = syncPoints.get(listId)
        val listRows = remote.fetchList(listId, since)
        val itemRows = remote.fetchItems(listId, since)
        apply(listRows, itemRows)
        // Advance the high-water mark only AFTER a full successful apply — a
        // failure above leaves it untouched and the next catch-up refetches.
        val newest = (listRows.mapNotNull { it.updatedAt } + itemRows.mapNotNull { it.updatedAt })
            .maxOrNull()
        if (newest != null) syncPoints.set(listId, newest)
    }

    // Discovery-first: the server's RLS-scoped view IS the account's list set
    // (see docs/technical-decisions/004). A fresh sign-in — or a brand-new
    // device — materialises its shared replicas right here; signed out, the
    // fetch comes back empty and this degrades to a no-op.
    // Full re-pull of one list: drop the high-water mark, then catch up from
    // scratch. join() uses this so re-joining REFRESHES the replica (spec:
    // "joining twice is idempotent — the existing replica is refreshed") —
    // plain catchUp starts from the stored sync point, which on a re-join is
    // already current and silently no-ops. Also the user-driven recovery road
    // if a replica ever goes bad: leave + re-enter the code.
    suspend fun fullCatchUp(listId: String): Result<Unit> {
        syncPoints.remove(listId)
        return catchUp(listId)
    }

    suspend fun catchUpAllShared(): Result<Unit> = safeCall {
        val discovered = remote.fetchAllLists()
        apply(discovered, emptyList())
        // Union with locally-known shared lists so a temporary server hiccup
        // (empty discovery) still refreshes what we already have.
        // Per-list failures are Result-wrapped and deliberately not
        // propagated: one unreachable list must not block the others, and the
        // next kick retries everything anyway.
        (discovered.map { it.id } + listDao.getSharedListIds()).distinct()
            .forEach { catchUp(it) }
    }

    // Explicit sign-out (and account deletion) ONLY — never session loss:
    // shared lists are account data (docs/technical-decisions/004), so they
    // leave with the account. Local-only lists are untouched. Best-effort
    // final push first, so a signed-in device's queued edits aren't lost;
    // its failure (offline sign-out) is the accepted rare² data-loss case.
    suspend fun evictSharedReplicas(): Result<Unit> = safeCall {
        push()
        // Hard local delete, not tombstones: this is eviction, not a deletion
        // to sync. CASCADE clears the items.
        listDao.deleteSharedLists()
        syncPoints.clear()
    }

    private suspend fun apply(listRows: List<ListRow>, itemRows: List<ItemRow>) {
        for (row in listRows) {
            val local = listDao.getListRaw(row.id)
            if (shouldSkip(local?.pendingSync, local?.updatedAt, row.updatedAt)) continue
            val entity = row.toEntity(local)
            // UPDATE, not insert-REPLACE, when the row already exists: REPLACE is
            // DELETE + INSERT in SQLite, which fires shopping_items' ON DELETE
            // CASCADE and hard-deletes every item of the list. A single renamed
            // list arriving from a peer used to wipe that list's items on every
            // device (including the one that made the rename, once its own push
            // echoed back) — unrecoverably, since the rows are gone rather than
            // tombstoned and the sync point has already moved past them.
            if (local == null) listDao.insertList(entity) else listDao.updateList(entity)
        }
        for (row in itemRows) {
            // FK guard: an item whose list isn't in Room yet can't be inserted
            // (CASCADE FK). The list row lands via the same catch-up that
            // brought the item, so the next pass picks the item up.
            if (listDao.getListRaw(row.listId) == null) continue
            val local = itemDao.getItemRaw(row.id)
            if (shouldSkip(local?.pendingSync, local?.updatedAt, row.updatedAt)) continue
            itemDao.insertItem(row.toEntity(local))
        }
    }

    // Incoming loses when: the local row is dirty (our pending push will
    // out-timestamp it at the server), or the incoming stamp is older than
    // what we already applied (stale/out-of-order fetch).
    private fun shouldSkip(localDirty: Boolean?, localUpdatedAt: Long?, incomingIso: String?): Boolean {
        if (incomingIso == null) return true // defensive: server rows always carry it
        if (localDirty == null || localUpdatedAt == null) return false // no local row → apply
        return localDirty || incomingIso.isoToMillis() < localUpdatedAt
    }
}
