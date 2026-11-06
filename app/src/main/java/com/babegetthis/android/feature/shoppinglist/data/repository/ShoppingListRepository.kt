package com.babegetthis.android.feature.shoppinglist.data.repository

import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.AppErrorException
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.SafeCall
import com.babegetthis.android.core.reminder.ReminderKicker
import com.babegetthis.android.core.sync.SyncKicker
import com.babegetthis.android.core.sync.data.repository.ListOwnershipStore
import com.babegetthis.android.core.sync.data.repository.SyncEngine
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppingitems.data.mapper.toEntity
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import com.babegetthis.android.feature.shoppinglist.data.mapper.toDomain
import com.babegetthis.android.feature.shoppinglist.data.mapper.toEntity
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingListRepository @Inject constructor(
    private val shoppingListDao: ShoppingListDao,
    private val shoppingItemDao: ShoppingItemDao,
    private val categoryDao: CategoryDao,
    private val authStateManager: AuthStateManager,
    private val ownership: ListOwnershipStore,
    private val syncKicker: SyncKicker,
    private val syncEngine: SyncEngine,
    private val reminderKicker: ReminderKicker,
    private val safeCall: SafeCall,
) {
    // Flows don't need Result wrapping — Room handles errors internally
    // and the Flow just stops emitting. We wrap write operations only.
    //
    // One owner's lists at a time (docs/technical-decisions/008): the account's
    // while signed in or after a lost session, the guest lists when signed out.
    // Re-queries whenever the owner changes, so signing in or out swaps the
    // screen's lists without anyone asking.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllLists(): Flow<List<ShoppingList>> = authStateManager.dataOwner
        .flatMapLatest { owner -> shoppingListDao.getListsWithItemCount(owner) }
        .map { list -> list.map { it.toDomain() } }

    // Every write to a list that has an owner goes through sync, shared or not
    // — and the items screen catches an account list up once when opened, since
    // the account's other phones may have changed it.
    suspend fun isAccountList(listId: String): Boolean = shoppingListDao.getListRaw(listId)?.accountId != null

    fun getListById(listId: String): Flow<ShoppingList?> = shoppingListDao.getListById(listId).map { it?.toDomain() }

    // Live share state of a list — null while it's local-only (and again if it
    // gets tombstoned; getListById filters tombstones). Drives the realtime
    // subscription lifecycle in the items ViewModel.
    fun getShareCode(listId: String): Flow<String?> = shoppingListDao.getListById(listId).map { it?.shareCode }

    suspend fun createList(name: String): Result<String> = safeCall {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val list = ShoppingList(
            id = id,
            // Validated and trimmed HERE, not only in the UI: this is the trust
            // boundary, and createListWithItems reaches the same table from
            // transcribed voice input that no dialog ever validated. A blank name
            // produces a list the user cannot identify or search for.
            name = name.requireListName(),
            createdAt = now,
            updatedAt = now,
        )
        val owner = authStateManager.dataOwnerId()
        shoppingListDao.insertList(list.toEntity().copy(accountId = owner, pendingSync = owner != null))
        if (owner != null) syncKicker.pushSoon()
        id
    }

    // Voice-capture entry point: create a new list AND drop its items in one go.
    // Mirrors the (list insert + items insert) shape of restoreListWithItems below,
    // and per-item defaults follow ShoppingItemRepository.addItem so voice items
    // look identical to manually-typed ones. Returns the new list id so the
    // ViewModel can navigate into it.
    suspend fun createListWithItems(name: String, drafts: List<ItemDraft>): Result<String> = safeCall {
        val now = System.currentTimeMillis()
        val listId = UUID.randomUUID().toString()

        val list = ShoppingList(
            id = listId,
            name = name.requireListName(),
            createdAt = now,
            updatedAt = now,
        )

        // One transaction. Two independent inserts left a list with none of its
        // items whenever anything failed or was cancelled in between — and the
        // voice sheet cancels this exact call on dismiss.
        val owner = authStateManager.dataOwnerId()
        val synced = owner != null
        shoppingListDao.insertListWithItems(
            list = list.toEntity().copy(accountId = owner, pendingSync = synced),
            items = draftsToItems(listId, drafts, now).map { it.copy(pendingSync = synced) },
        )
        if (synced) syncKicker.pushSoon()

        listId
    }

    // Shared draft → item-entity mapping, used by both createListWithItems and
    // addItemsToList. Centralises the trust-boundary guard (keep a backend
    // category id only if it's a real row) so it lives in ONE place instead of
    // being copy-pasted. suspend because it snapshots the live categories Flow
    // via first().
    private suspend fun draftsToItems(listId: String, drafts: List<ItemDraft>, now: Long): List<ShoppingItemEntity> {
        val knownCategoryIds = categoryDao.getAllCategories().first().mapTo(HashSet()) { it.id }
        return drafts.map { draft ->
            ShoppingItem(
                id = UUID.randomUUID().toString(),
                listId = listId,
                name = draft.name,
                // Backend returns quantity + unit already flattened into draft.quantity.
                // null → empty string to match manually-typed items.
                quantity = draft.quantity.orEmpty(),
                // Keep the backend's category id only if it's a known row; unknown/null → uncategorized.
                categoryId = draft.category?.takeIf { it in knownCategoryIds },
                note = draft.note,
                shop = draft.shop,
                createdAt = now,
                updatedAt = now,
            ).toEntity()
        }
    }

    // Voice "add to existing list": append spoken drafts to a list the user is
    // already viewing. No list is created and no navigation happens — the rows
    // just materialise in the open list via its Room Flow. Returns the listId so
    // the voice VM can transition to Done, mirroring createListWithItems.
    suspend fun addItemsToList(listId: String, drafts: List<ItemDraft>): Result<String> = safeCall {
        val now = System.currentTimeMillis()
        val synced = isAccountList(listId)
        val itemEntities = draftsToItems(listId, drafts, now)
            .map { if (synced) it.copy(pendingSync = true) else it }
        if (itemEntities.isNotEmpty()) {
            shoppingItemDao.insertItems(itemEntities)
            if (synced) syncKicker.pushSoon()
            reminderKicker.onLocalEdit(listId)
        }
        listId
    }

    // Trim and reject blank list names at the repository boundary. Throws through
    // safeCall as a ValidationError, so callers get the normal Result.Error path
    // rather than a crash or a silently-blank list.
    private fun String.requireListName(): String {
        val trimmed = trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.ValidationError("List name can't be empty."))
        }
        return trimmed
    }

    private fun listNotFoundException(listId: String) =
        AppErrorException(AppError.NotFoundError("That list no longer exists."))

    // The lock is per-device and never syncs, so signing out (which evicts an
    // account list) would lose it. Remembering the id re-locks the list when it
    // comes back (docs/technical-decisions/008).
    suspend fun setLocked(listId: String, locked: Boolean): Result<Unit> = safeCall {
        shoppingListDao.setLocked(listId, locked)
        ownership.rememberLock(listId, locked)
    }

    // Unlock every list — called after the device PIN is removed so no list is
    // left gated by a PIN that no longer exists.
    suspend fun unlockAll(): Result<Unit> = safeCall {
        shoppingListDao.unlockAll()
        ownership.forgetAllLocks()
    }

    // How many lists are currently locked — Settings shows this before PIN
    // removal so the user knows the blast radius. Counted in SQL rather than by
    // collecting every list with its joined item counts and filtering in Kotlin.
    suspend fun lockedCount(): Int = shoppingListDao.lockedCount()

    suspend fun updateListName(listId: String, newName: String): Result<Unit> = safeCall {
        val now = System.currentTimeMillis()
        val entity = shoppingListDao.getListById(listId).first()
            // NotFoundError rather than the bare IllegalStateException this used to
            // throw — safeCall maps unrecognised exceptions to UnknownError carrying
            // the raw exception text, so "List not found" was rendered to the user
            // verbatim in a snackbar.
            ?: throw listNotFoundException(listId)
        val synced = entity.accountId != null
        shoppingListDao.updateList(
            entity.copy(
                name = newName.requireListName(),
                updatedAt = now,
                pendingSync = entity.pendingSync || synced,
            ),
        )
        if (synced) syncKicker.pushSoon()
    }

    // Captures items before deleting the list. The shopping_items foreign key
    // CASCADEs on list delete, so items are wiped from the DB — we return them
    // so the caller can hold them for undo and re-insert if needed.
    // Account lists soft-delete instead: the tombstone must survive (and sync)
    // for the deletion to reach other members and the account's other phones,
    // and the item rows stay in Room hidden behind the tombstoned list. Even
    // one that hasn't reached the server yet — a push may be uploading it right
    // now, and only the push, which is serialised with that upload, can tell
    // whether the tombstone has to follow it or the row can simply go
    // (SyncEngine.push drops it without uploading).
    suspend fun deleteListAndCaptureItems(listId: String): Result<List<ShoppingItemEntity>> = safeCall {
        val items = shoppingItemDao.getItemsByListIdOnce(listId)
        if (isAccountList(listId)) {
            shoppingListDao.softDeleteList(listId, now = System.currentTimeMillis())
            syncKicker.pushSoon()
        } else {
            shoppingListDao.deleteList(listId)
        }
        items
    }

    // Delete a list ONLY if it has no items. Used when the user leaves a list
    // detail screen with nothing in it. The check-and-delete is a single
    // atomic SQL statement (see the DAO), so a list that has items — or gains
    // one mid-flight — is never deleted by mistake.
    // Returns true if the list was empty and got deleted.
    suspend fun deleteListIfEmpty(listId: String): Result<Boolean> = safeCall {
        val raw = shoppingListDao.getListRaw(listId) ?: return@safeCall false
        when {
            // Never auto-delete a shared list: "empty" on this phone says nothing
            // about what the partner is doing with it right now.
            raw.shareCode != null -> false
            raw.accountId == null -> shoppingListDao.deleteListIfEmpty(listId) > 0
            // An unshared account list is only this user's, so it can go — as a
            // tombstone, so the account's other phones lose it too. One that
            // never reached the server has no other phones to tell, and the push
            // drops its tombstone instead of uploading it (SyncEngine.push).
            !raw.onServer -> softDeleteIfEmpty(listId)
            // On the server, so the account's other phones may have added to it
            // while this one wasn't looking — "empty here" is only half the
            // story, and the deletion would reach them with no undo. Ask the
            // server first; with no session or no signal the list stays.
            else -> {
                if (authStateManager.liveSessionUserId() == null) return@safeCall false
                if (syncEngine.catchUp(listId) is Result.Error) return@safeCall false
                softDeleteIfEmpty(listId)
            }
        }
    }

    private suspend fun softDeleteIfEmpty(listId: String): Boolean {
        val deleted = shoppingListDao.softDeleteListIfEmpty(listId, now = System.currentTimeMillis()) > 0
        if (deleted) syncKicker.pushSoon()
        return deleted
    }

    // Re-insert a previously deleted list along with its items (for undo).
    // Restores both so derived fields like isCompleted recompute correctly.
    suspend fun restoreListWithItems(list: ShoppingList, items: List<ShoppingItemEntity>): Result<Unit> = safeCall {
        val raw = shoppingListDao.getListRaw(list.id)
        if (raw?.accountId != null) {
            // Account list, soft-deleted: the rows never left Room, so undo is
            // just clearing the tombstone (and pushing so members and the
            // account's other phones get it back too). Re-inserting from the
            // domain model instead would wipe accountId and shareCode and
            // silently detach the list from sync. UPDATE, not insertList: that
            // is a REPLACE — DELETE + INSERT — whose CASCADE wiped every item of
            // the restored list (REALTIME_TEST_FINDINGS.md BUG 1, same shape).
            shoppingListDao.updateList(
                raw.copy(deletedAt = null, updatedAt = System.currentTimeMillis(), pendingSync = true),
            )
            syncKicker.pushSoon()
        } else {
            // The row is gone: a guest list, or an account list the push already
            // dropped because it never reached the server (SyncEngine.push). It
            // comes back as a list of whoever owns the device's lists now, as a
            // new list would — the domain model carries no owner. One
            // transaction — a half-completed restore would put the list back
            // empty and drop exactly the items the undo cache exists to protect.
            val owner = authStateManager.dataOwnerId()
            shoppingListDao.insertListWithItems(
                list.toEntity().copy(accountId = owner, pendingSync = owner != null),
                if (owner == null) items else items.map { it.copy(pendingSync = true) },
            )
            if (owner != null) syncKicker.pushSoon()
        }
    }
}
