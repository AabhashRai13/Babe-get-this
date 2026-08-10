package com.babegetthis.android.testing

import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.core.sync.data.remote.SharedListRemote
import com.babegetthis.android.core.sync.data.repository.SyncPointStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

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

    override suspend fun fetchItems(listId: String, sinceIso: String?): List<ItemRow> {
        if (failFetches) error("network down")
        lastItemSince = sinceIso
        return itemRows.filter { it.listId == listId }
    }

    override suspend fun fetchAllLists(): List<ListRow> {
        if (failFetches) error("network down")
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
    override fun set(listId: String, iso: String) { points[listId] = iso }
    override fun remove(listId: String) { points.remove(listId) }
    override fun clear() { points.clear() }
}
