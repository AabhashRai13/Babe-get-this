package com.babegetthis.android.core.sync.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder

// Rows per request. Equal to Supabase's default PostgREST max_rows so
// a full page is exactly one request; it must never be set above the
// project's max_rows (see fetchAllPaged).
internal const val PAGE_SIZE = 1000

// The whole result, a page at a time. Keyset paging on (updated_at, id)
// rather than offsets: a row updated while paging moves to the end of the
// order, and with offsets that shifts every row behind it by one — the
// one that slips across the page boundary is never returned. With a cursor
// the moved row simply shows up in a later page (or the next pass).
// Stops at the first short page; a page the server cut short at max_rows
// would look complete, so PAGE_SIZE must not exceed it.
internal suspend inline fun <reified T : Any> SupabaseClient.fetchAllPaged(
    table: String,
    cursorOf: (T) -> Pair<String?, String>,
    crossinline where: PostgrestFilterBuilder.() -> Unit,
): List<T> {
    val rows = mutableListOf<T>()
    var after: Pair<String?, String>? = null
    while (true) {
        val cursor = after
        val page = from(table).select {
            filter {
                where()
                val (stamp, id) = cursor ?: return@filter
                // Everything strictly after the last row seen, in the same
                // order the rows are returned in.
                or {
                    gt("updated_at", stamp ?: "")
                    and {
                        eq("updated_at", stamp ?: "")
                        gt("id", id)
                    }
                }
            }
            order("updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            limit(PAGE_SIZE.toLong())
        }.decodeList<T>()
        rows += page
        if (page.size < PAGE_SIZE) return rows
        after = cursorOf(page.last())
    }
}
