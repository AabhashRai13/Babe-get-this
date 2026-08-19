package com.babegetthis.android.core.sync.data.remote

import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import kotlinx.coroutines.flow.Flow

// Thin seam over Supabase so SyncEngine is testable with a fake. No logic
// belongs here — anything with a branch goes in the engine, under the
// coverage gate.
interface SharedListRemote {

    // Plain INSERT for the FIRST share of a list — deliberately not upsert:
    // Postgres evaluates the upsert's update-arm policy (is_list_member)
    // against the incoming row BEFORE the creator-membership AFTER-INSERT
    // trigger has run, so upserting a brand-new list always fails RLS.
    // Returns false when the row already exists (a previous share reached the
    // server but never persisted locally) — caller recovers via fetchList.
    suspend fun insertList(row: ListRow): Boolean

    // Upsert is fine for every later push: the row exists, membership exists.
    suspend fun upsertLists(rows: List<ListRow>)

    suspend fun upsertItems(rows: List<ItemRow>)

    // sinceIso null = full pull (first join). Otherwise rows with
    // updated_at >= since — gte, not gt: re-fetching the newest already-seen
    // row is harmless (apply is idempotent) and closes the equal-timestamp gap.
    suspend fun fetchList(listId: String, sinceIso: String?): List<ListRow>

    suspend fun fetchItems(listId: String, sinceIso: String?): List<ItemRow>

    // Discovery: every list row the current session may see — RLS scopes it
    // to the account's memberships. Signed out → empty.
    suspend fun fetchAllLists(): List<ListRow>

    // Returns the joined list's id, or null when the code matches no list.
    // Other failures (network, signed out) throw.
    suspend fun joinListByCode(code: String): String?

    // Emits Unit whenever the list or its items change server-side, AND on
    // every (re)join of the underlying channel. Collectors respond to every
    // emission with a catch-up — realtime never carries data itself, it only
    // says "worth asking". Missed-while-disconnected events are therefore a
    // non-problem by construction.
    fun changes(listId: String): Flow<Unit>
}
