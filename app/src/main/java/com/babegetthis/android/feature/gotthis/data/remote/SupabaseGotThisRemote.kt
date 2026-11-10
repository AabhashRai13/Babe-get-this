package com.babegetthis.android.feature.gotthis.data.remote

import com.babegetthis.android.core.sync.data.remote.fetchAllPaged
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

class SupabaseGotThisRemote(private val client: SupabaseClient) : GotThisRemote {

    override suspend fun recordTicks(events: List<TickEventRow>): TickReply = client.postgrest.rpc(
        function = "record_ticks",
        parameters = buildJsonObject {
            put("p_events", Json.encodeToJsonElement(events))
        },
    ).decodeAs<TickReplyDto>().let { TickReply(it.accepted, it.retry, it.rejected) }

    override suspend fun uploadOwnPurchases(rows: List<OwnPurchaseRow>) {
        client.postgrest.rpc(
            function = "upload_own_purchases",
            parameters = buildJsonObject {
                put("p_rows", Json.encodeToJsonElement(rows))
            },
        )
    }

    override suspend fun clearHistory(clearedAtIso: String): String = client.postgrest.rpc(
        function = "clear_purchase_history",
        parameters = buildJsonObject { put("p_cleared_at", clearedAtIso) },
    ).decodeAs<String>()

    // RLS leaves only the caller's own row visible, so no filter is needed.
    override suspend fun fetchResetAt(): String? =
        client.from("purchase_history_resets").select().decodeList<ResetDto>().firstOrNull()?.resetAt

    override suspend fun fetchPurchases(sinceIso: String?): List<PurchaseRow> =
        client.fetchAllPaged<PurchaseRow>("purchases", cursorOf = { it.updatedAt to it.id }) {
            if (sinceIso != null) gte("updated_at", sinceIso)
        }

    override suspend fun claimRunningLowWeek(isoWeek: String): Boolean = client.postgrest.rpc(
        function = "claim_running_low_week",
        parameters = buildJsonObject { put("p_week", isoWeek) },
    ).decodeAs<Boolean>()

    @Serializable
    private data class TickReplyDto(
        val accepted: List<String> = emptyList(),
        val retry: List<String> = emptyList(),
        val rejected: List<String> = emptyList(),
    )

    @Serializable
    private data class ResetDto(@SerialName("reset_at") val resetAt: String)
}
