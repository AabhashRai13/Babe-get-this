package com.babegetthis.android.core.sync.data.remote

import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseSharedListRemote(private val client: SupabaseClient) : SharedListRemote {

    override suspend fun insertList(row: ListRow): Boolean = try {
        client.from("lists").insert(row)
        true
    } catch (e: RestException) {
        // 23505 = unique violation: the list row (or, astronomically, the
        // share code) already exists server-side.
        val duplicate = e.message?.let { it.contains("23505") || it.contains("duplicate key") } == true
        if (duplicate) false else throw e
    }

    override suspend fun upsertLists(rows: List<ListRow>) {
        client.from("lists").upsert(rows)
    }

    override suspend fun upsertItems(rows: List<ItemRow>) {
        client.from("items").upsert(rows)
    }

    override suspend fun fetchList(listId: String, sinceIso: String?): List<ListRow> =
        client.from("lists").select {
            filter {
                eq("id", listId)
                if (sinceIso != null) gte("updated_at", sinceIso)
            }
        }.decodeList<ListRow>()

    override suspend fun fetchItems(listId: String, sinceIso: String?): List<ItemRow> =
        client.from("items").select {
            filter {
                eq("list_id", listId)
                if (sinceIso != null) gte("updated_at", sinceIso)
            }
        }.decodeList<ItemRow>()

    override suspend fun fetchAllLists(): List<ListRow> =
        client.from("lists").select().decodeList<ListRow>()

    override suspend fun joinListByCode(code: String): String? = try {
        client.postgrest.rpc(
            function = "join_list_by_code",
            parameters = buildJsonObject { put("p_code", code) },
        ).decodeAs<String>()
    } catch (e: RestException) {
        // The RPC raises 'invalid_code' for a code that matches nothing —
        // that's a user-facing outcome, not an infrastructure failure.
        if (e.message?.contains("invalid_code") == true) null else throw e
    }

    override fun changes(listId: String): Flow<Unit> = flow {
        val channel = client.channel("list-$listId")
        val events = merge(
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "lists"
                filter("id", FilterOperator.EQ, listId)
            },
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "items"
                filter("list_id", FilterOperator.EQ, listId)
            },
        ).map { }
        // Joins (initial AND automatic re-joins after a socket drop) also emit,
        // so a collector doing catch-up-per-emission is complete without any
        // special reconnect handling.
        val joins = channel.status
            .filter { it == RealtimeChannel.Status.SUBSCRIBED }
            .map { }
        try {
            channel.subscribe()
            emitAll(merge(events, joins))
        } finally {
            withContext(NonCancellable) { client.realtime.removeChannel(channel) }
        }
    }
}
