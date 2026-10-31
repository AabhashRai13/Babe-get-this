package com.babegetthis.android.testing

import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.core.sync.data.remote.SharedListRemote
import com.babegetthis.android.core.sync.data.repository.ListOwnershipStore
import com.babegetthis.android.core.sync.data.repository.SyncPointStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Shared by SyncEngineTest and ShareRepositoryTest — one knobbed fake instead
// of two private near-copies.
class FakeSharedListRemote : SharedListRemote {
    val upsertedLists = mutableListOf<ListRow>()
    val upsertedItems = mutableListOf<ItemRow>()
    val fetchedListIds = mutableListOf<String>()
    var listRows: List<ListRow> = emptyList()
    var itemRows: List<ItemRow> = emptyList()
    var lastListSince: String? = null
    var lastItemSince: String? = null
    var failUpserts = false
    var failItemUpserts = false
    var failFetches = false
    var joinResult: String? = null
    var joinCalledWith: String? = null
    var onUpsertItems: suspend () -> Unit = {}

    val insertedLists = mutableListOf<ListRow>()
    var listAlreadyExists = false

    override suspend fun insertList(row: ListRow): Boolean {
        if (failUpserts) error("network down")
        if (listAlreadyExists) return false
        insertedLists += row
        return true
    }

    override suspend fun upsertLists(rows: List<ListRow>) {
        if (failUpserts) error("network down")
        upsertedLists += rows
    }

    override suspend fun upsertItems(rows: List<ItemRow>) {
        if (failUpserts || failItemUpserts) error("network down")
        onUpsertItems()
        upsertedItems += rows
    }

    override suspend fun fetchList(listId: String, sinceIso: String?): List<ListRow> {
        if (failFetches) error("network down")
        fetchedListIds += listId
        lastListSince = sinceIso
        return listRows.filter { it.id == listId }
    }

    val itemFetchListIds = mutableListOf<String>()

    // Every item read of an account catch-up, in the order it was made:
    // "account" for the account-wide request, "items:<listId>" for a full pull.
    val itemReads = mutableListOf<String>()

    // Fail the full item pull of this one list (a dropped request mid-pass).
    var failItemFetchFor: String? = null

    override suspend fun fetchItems(listId: String, sinceIso: String?): List<ItemRow> {
        if (failFetches || listId == failItemFetchFor) error("network down")
        itemFetchListIds += listId
        itemReads += "items:$listId"
        lastItemSince = sinceIso
        return itemRows.filter { it.listId == listId }
    }

    // The since of each account-wide item request, in order. Rows are filtered
    // by it the way the server's gte would.
    val accountItemSinces = mutableListOf<String?>()
    var failAccountItems = false

    override suspend fun fetchAccountItems(sinceIso: String?): List<ItemRow> {
        if (failFetches || failAccountItems) error("network down")
        accountItemSinces += sinceIso
        itemReads += "account"
        return itemRows.filter { sinceIso == null || (it.updatedAt ?: "") >= sinceIso }
    }

    // (listId, code) per share request. serverShareCode simulates another
    // phone having shared first (the write-once trigger keeps its code);
    // shareCodeRowMissing simulates a row the session can't see.
    val shareCodeRequests = mutableListOf<Pair<String, String>>()
    var serverShareCode: String? = null
    var shareCodeRowMissing = false

    override suspend fun setShareCode(listId: String, code: String): String? {
        if (failUpserts) error("network down")
        shareCodeRequests += listId to code
        if (shareCodeRowMissing) return null
        return serverShareCode ?: code
    }

    // Completed when discovery is entered; a gate it then waits on. Lets a
    // test hold a catch-up mid-request while something else tries to run.
    val fetchAllEntered = CompletableDeferred<Unit>()
    var fetchAllGate: CompletableDeferred<Unit>? = null

    override suspend fun fetchAllLists(): List<ListRow> {
        if (failFetches) error("network down")
        fetchAllEntered.complete(Unit)
        fetchAllGate?.await()
        return listRows
    }

    override suspend fun joinListByCode(code: String): String? {
        joinCalledWith = code
        return joinResult
    }

    // Emit into this to simulate realtime events for subscribed collectors.
    val changeEvents = MutableSharedFlow<Unit>()

    override fun changes(listId: String): Flow<Unit> = changeEvents
}

class FakeSyncPointStore : SyncPointStore {
    val points = mutableMapOf<String, String>()
    override fun get(listId: String): String? = points[listId]
    override fun set(listId: String, iso: String) {
        points[listId] = iso
    }
    override fun remove(listId: String) {
        points.remove(listId)
    }
    override fun clear() {
        points.clear()
    }
}

class FakeListOwnershipStore : ListOwnershipStore {
    private val notice = MutableStateFlow(0)
    override val adoptionNotice: StateFlow<Int> = notice
    override fun setAdoptionNotice(count: Int) {
        notice.value = count
    }

    var migrated = false
    override fun isAccountListsMigrated(): Boolean = migrated
    override fun markAccountListsMigrated() {
        migrated = true
    }

    private val locks = mutableSetOf<String>()
    override fun lockedListIds(): Set<String> = locks.toSet()
    override fun rememberLock(listId: String, locked: Boolean) {
        if (locked) locks += listId else locks -= listId
    }
    override fun forgetAllLocks() {
        locks.clear()
    }

    private var unverified = emptySet<String>()
    override fun unverifiedListIds(): Set<String> = unverified
    override fun setUnverifiedListIds(ids: Set<String>) {
        unverified = ids
    }
}
