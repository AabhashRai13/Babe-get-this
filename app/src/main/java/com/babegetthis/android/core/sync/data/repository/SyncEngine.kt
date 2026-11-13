package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.SafeCall
import com.babegetthis.android.core.sync.data.mapper.isoToMillis
import com.babegetthis.android.core.sync.data.mapper.toEntity
import com.babegetthis.android.core.sync.data.mapper.toRow
import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.core.sync.data.remote.SharedListRemote
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.feature.gotthis.data.GotThisSync
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val ownership: ListOwnershipStore,
    private val gotThis: GotThisSync,
    private val currentUserId: () -> String?,
    private val crashReporter: CrashReporter,
    private val safeCall: SafeCall,
) {

    // One operation at a time. Push, catch-up, the sign-in claim and the
    // sign-out eviction all read and write the same rows, and two of them
    // interleaved undo each other: a catch-up still in flight while sign-out
    // evicts the account's lists would land them again afterwards, and a
    // delete racing a first INSERT could leave a list on the server that this
    // phone has forgotten. Every public entry point takes the lock; the
    // *Locked helpers are what runs inside it, so nothing nests.
    private val mutex = Mutex()

    // Push everything pendingSync that belongs to the signed-in account —
    // every account list, shared or not (docs/technical-decisions/008). Lists
    // strictly before items: the server's creator-membership trigger on `lists`
    // is what makes the item RLS checks pass (see the SQL migration's client
    // contract note). No live session → no-op; the queue just waits.
    suspend fun push(): Result<Unit> = safeCall { mutex.withLock { pushLocked() } }

    private suspend fun pushLocked() {
        // A list deleted before it ever reached the server has nothing to tell
        // anyone. Uploading its tombstone would create a server row (plus a
        // membership row and its items) that no pass ever purges and every
        // discovery returns, so it goes here instead — under the lock, so it
        // can't race the INSERT that would have put it on the server. Needs no
        // session: it is outbox housekeeping, and it keeps the sign-out
        // warning honest for a session that has expired.
        gotThis.dropNeverSentLists()
        val userId = currentUserId() ?: return
        val lists = listDao.getPendingAccountLists(userId)
        val (existing, brandNew) = lists.partition { it.onServer }
        // A brand-new row must be a plain INSERT — an upsert of it fails RLS
        // (SharedListRemote.insertList). "Already there" (false) means an
        // earlier push landed before the app could record it; the row may have
        // changed since, so it joins the upsert below like any row already on
        // the server, instead of the server keeping that first version.
        val upserts = existing.toMutableList()
        brandNew.forEach { list ->
            if (!remote.insertList(list.toRow(createdBy = userId))) upserts += list
            listDao.markListOnServer(list.id)
        }
        if (upserts.isNotEmpty()) {
            remote.upsertLists(upserts.map { it.toRow(createdBy = userId) })
        }
        // Guarded per-row clear: a row edited again mid-push keeps its
        // flag (updatedAt no longer matches) and re-pushes next kick.
        lists.forEach { listDao.markListSynced(it.id, it.updatedAt) }
        val items = itemDao.getPendingSyncItemsForAccount(userId)
        if (items.isNotEmpty()) {
            remote.upsertItems(items.map { it.toRow() })
            items.forEach { itemDao.markItemSynced(it.id, it.updatedAt) }
        }
        isolatedGotThis { gotThis.push(userId) }
        // Counts only. A crash mid-sync reads very differently depending on
        // whether the queue was empty or draining hundreds of rows, and
        // that is not recoverable from the report afterwards.
        crashReporter.breadcrumb("sync: pushed ${'$'}{lists.size} lists, ${'$'}{items.size} items")
    }

    // Got This is an extra on top of lists and items. A failure in it (a server
    // function not deployed yet, a rate limit) must not fail the whole pass: the
    // lists synced, and the retry loop would fire for nothing. Its own queues
    // (the tick outbox, pending uploads, a pending clear) stay as they were and
    // go out on the next pass.
    private suspend fun isolatedGotThis(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            crashReporter.breadcrumb("sync: got this failed")
        }
    }

    // Give the device's lists to the account that just signed in (or whose
    // session expired — the owner is still known). Local only, so it works
    // offline: an already-signed-in user opening the update in a store must see
    // their lists at once (docs/technical-decisions/008). Returns how many guest
    // lists moved in, and adds them to the notice the lists screen shows.
    suspend fun claimDeviceLists(userId: String): Result<Int> = safeCall {
        mutex.withLock {
            // Before the lists change hands, so guest ticks have their times.
            gotThis.prepareClaim()
            listDao.getOtherAccountListIds(userId).forEach { syncPoints.remove(it) }
            // The first claim after the update also takes the existing shared
            // replicas: they predate owners, and they belong to whoever is signed
            // in right now. Later, an ownerless replica can only be a stray from
            // a lost session, and waits for the server to vouch for it (apply()).
            val firstClaim = !ownership.isAccountListsMigrated()
            // Taken on trust, so remembered: one left by a different account
            // would fail RLS on every push and stall the whole queue. The first
            // discovery keeps the ones the server returns (catchUpAccount).
            if (firstClaim) ownership.setUnverifiedListIds(listDao.getOwnerlessSharedListIds().toSet())
            val claimed = listDao.claimDeviceLists(userId, includeOwnerlessShared = firstClaim)
            finishFirstRun()
            // After the first-run bookkeeping: a failure here must not skip it.
            gotThis.claimGuestHistory(userId)
            if (claimed > 0) ownership.setAdoptionNotice(ownership.adoptionNotice.value + claimed)
            // Count only. Which lists moved is the user's business.
            crashReporter.breadcrumb("sync: claimed $claimed lists")
            claimed
        }
    }

    // What a sign-in state means for this device's lists. Returns true when
    // there is a live session to sync with — the caller kicks a sync.
    suspend fun onAuthState(state: AuthState): Boolean = when (state) {
        is AuthState.Authenticated -> {
            claimDeviceLists(state.userId)
            true
        }
        is AuthState.SessionExpired -> {
            claimDeviceLists(state.userId)
            false
        }
        AuthState.Unauthenticated -> {
            // A signed-out first launch after the update: nothing to claim, and
            // any shared replica left on the device is a stray — never take it
            // wholesale at a later sign-in.
            mutex.withLock { finishFirstRun() }
            false
        }
        AuthState.Loading -> false
    }

    // The server has answered: first-run claims it didn't return aren't this
    // account's, so let them go before they are ever pushed again.
    private suspend fun releaseUnverifiedClaims(userId: String, confirmed: Set<String>) {
        val unverified = ownership.unverifiedListIds()
        if (unverified.isEmpty()) return
        listDao.releaseClaims((unverified - confirmed).toList(), userId)
        ownership.setUnverifiedListIds(emptySet())
    }

    // Once per install, after the ownership update. Locks set before it never
    // went through setLocked, so they aren't in the remembered set that
    // re-locks a list sign-out evicted; record them before marking the run done.
    private suspend fun finishFirstRun() {
        if (ownership.isAccountListsMigrated()) return
        listDao.getLockedListIds().forEach { ownership.rememberLock(it, locked = true) }
        ownership.markAccountListsMigrated()
    }

    // Fetch everything changed in one list since its sync point and land it in
    // Room. Used for an open list (and on every realtime event of a shared
    // one): realtime events don't carry data anywhere — they just trigger
    // this. Idempotent by construction, so gte-refetch of the newest known row
    // is harmless. No live session → nothing to ask.
    suspend fun catchUp(listId: String): Result<Unit> = safeCall {
        mutex.withLock {
            val userId = currentUserId() ?: return@withLock
            val since = syncPoints.get(listId)
            val listRows = remote.fetchList(listId, since)
            val itemRows = remote.fetchItems(listId, since)
            apply(userId, listRows, itemRows)
            // Advance the high-water mark only AFTER a full successful apply — a
            // failure above leaves it untouched and the next catch-up refetches.
            val newest = (listRows.mapNotNull { it.updatedAt } + itemRows.mapNotNull { it.updatedAt })
                .maxOrNull()
            if (newest != null) syncPoints.set(listId, newest)
            // No list id: it identifies one shared list, and a shared list is a
            // thing two named people have between them.
            crashReporter.breadcrumb("sync: applied ${'$'}{listRows.size} lists, ${'$'}{itemRows.size} items")
        }
    }

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

    // The whole account in a handful of requests — every account list syncs
    // now, so two requests per list on every app open would not scale
    // (docs/technical-decisions/008).
    //
    // Discovery-first: the server's RLS-scoped view IS the account's list set.
    // It fetches every list row, not "changed since": joining a list adds a
    // membership row without touching the list's updated_at, so a since-filter
    // would never discover it. The items changed since ONE account-wide sync
    // point come in one request too (RLS scopes it to the account's lists the
    // same way); lists new to this phone then get their full item history. The
    // point moves only after everything landed, so a failed pass is redone.
    suspend fun catchUpAccount(): Result<Unit> = safeCall {
        mutex.withLock {
            val userId = currentUserId() ?: return@withLock
            val known = listDao.getAccountListIds(userId).toSet()
            val discovered = remote.fetchAllLists()
            val (fresh, rest) = discovered.partition { it.id !in known && it.deletedAt == null }
            apply(userId, rest, emptyList())
            // An empty answer is also what a request without a session gets
            // (RLS filters, it doesn't fail), and a kick can run before the
            // stored session has loaded. Only an answer with rows in it says
            // which first-run claims are real.
            if (discovered.isNotEmpty()) {
                releaseUnverifiedClaims(userId, confirmed = discovered.mapTo(HashSet()) { it.id })
            }

            // Known lists BEFORE the lists new to this phone, and the sync point
            // from what was read before any full pull: every stamp in it is
            // older than every full pull, so a change to a new list right after
            // its pull is newer than the point and the next pass fetches it.
            // Read the other way round, a batch read after the pull could set a
            // point past that change, and the account pass would skip it for
            // good. The discovered list rows were read first of all, so their
            // stamps are safe in the point too — which is what moves it along
            // on a pass with no item changes.
            val since = syncPoints.get(ACCOUNT_SYNC_POINT)
            val rows = if (known.isEmpty()) emptyList() else remote.fetchAccountItems(since)
            apply(userId, emptyList(), rows)
            val newest = (rows.mapNotNull { it.updatedAt } + discovered.mapNotNull { it.updatedAt }).maxOrNull()
            if (newest != null) syncPoints.set(ACCOUNT_SYNC_POINT, newest)

            // A list new to this phone lands together with its whole history,
            // never before it: once its row is in Room it counts as known, and a
            // known list only gets items newer than the account sync point. If
            // the pull fails, the row stays out and the next pass tries again.
            fresh.forEach { apply(userId, listOf(it), remote.fetchItems(it.id, sinceIso = null)) }
            isolatedGotThis { gotThis.pull(userId) }
            // Counts only, like every other sync breadcrumb.
            crashReporter.breadcrumb("sync: account catch-up over ${known.size} lists, ${discovered.size} discovered")
        }
    }

    // Explicit sign-out (and account deletion) ONLY — never session loss:
    // account lists, shared or not, leave with the account
    // (docs/technical-decisions/008). Guest lists are the device's and stay.
    // Best-effort final push first, so queued edits aren't lost; when it can't
    // run (offline), ProfileViewModel has already warned and the user chose.
    //
    // `andThen` runs inside the same critical section, for the sign-out
    // itself: a sync kick waiting on the lock would otherwise run between the
    // eviction and the sign-out, with the session still live, and discovery
    // would land the account's lists all over again.
    suspend fun evictAccountLists(andThen: suspend () -> Unit = {}): Result<Unit> = safeCall {
        mutex.withLock {
            safeCall { pushLocked() }
            // Hard local delete, not tombstones: this is eviction, not a deletion
            // to sync. CASCADE clears the items.
            gotThis.evict()
            listDao.deleteAccountLists()
            syncPoints.clear()
            crashReporter.breadcrumb("sync: evicted account lists")
            andThen()
        }
    }

    // Anything of this account's still waiting to reach the server — the rows
    // push would send, which eviction deletes. Rows push can never send (a
    // released first-run claim, another account's leftovers) aren't counted:
    // a warning about them could never be cleared by going online.
    suspend fun hasUnsyncedChanges(accountId: String): Boolean =
        listDao.countPendingAccountLists(accountId) + itemDao.countPendingSyncItemsForAccount(accountId) +
            gotThis.unsyncedCount(accountId) > 0

    private suspend fun apply(userId: String, listRows: List<ListRow>, itemRows: List<ItemRow>) {
        val lockedIds = ownership.lockedListIds()
        for (row in listRows) {
            val local = listDao.getListRaw(row.id)
            // The deletion of a list this phone never had: nothing to hide, so
            // nothing to store.
            if (local == null && row.deletedAt != null) continue
            // A replica with no owner (its session was lost before lists had
            // owners) is claimed the moment the server returns it for this
            // account — that answer is the proof of membership. Even when the
            // local row is dirty and the incoming data loses below.
            if (local != null && local.accountId == null) listDao.claimList(row.id, userId)
            if (shouldSkip(local?.pendingSync, local?.updatedAt, row.updatedAt)) continue
            val entity = row.toEntity(local, accountId = userId, lockedIds = lockedIds)
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
            if (local == null && row.deletedAt != null) continue
            if (shouldSkip(local?.pendingSync, local?.updatedAt, row.updatedAt)) continue
            itemDao.insertItem(row.toEntity(local))
        }
    }

    // Incoming loses when: the local row is dirty (our pending push will
    // out-timestamp it at the server), or the incoming stamp is no newer than
    // what we already applied (the same row again — discovery returns every
    // row on every pass — or a stale/out-of-order fetch).
    private fun shouldSkip(localDirty: Boolean?, localUpdatedAt: Long?, incomingIso: String?): Boolean {
        if (incomingIso == null) return true // defensive: server rows always carry it
        if (localDirty == null || localUpdatedAt == null) return false // no local row → apply
        return localDirty || incomingIso.isoToMillis() <= localUpdatedAt
    }

    companion object {
        // Key of the account-wide high-water mark in SyncPointStore. Every other
        // key is a list id (a UUID), so this can't collide.
        const val ACCOUNT_SYNC_POINT = "account"
    }
}
