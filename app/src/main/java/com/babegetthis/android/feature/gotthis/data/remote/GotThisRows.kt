package com.babegetthis.android.feature.gotthis.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// One tick (or its undo) as sent to record_ticks. Timestamps are ISO strings.
@Serializable
data class TickEventRow(
    val id: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("list_id") val listId: String,
    @SerialName("ticked_at") val tickedAt: String,
    val name: String,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("undone_at") val undoneAt: String?,
)

// A purchase the user made on their own device, uploaded to rebuild history.
@Serializable
data class OwnPurchaseRow(
    @SerialName("tick_id") val tickId: String,
    @SerialName("source_item_id") val sourceItemId: String,
    val name: String,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("bought_at") val boughtAt: String,
)

// A row of the purchases table.
@Serializable
data class PurchaseRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("tick_id") val tickId: String,
    @SerialName("source_item_id") val sourceItemId: String,
    val name: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("bought_at") val boughtAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

// The server's verdict per event id: stored, try again later, or never.
data class TickReply(val accepted: List<String>, val retry: List<String>, val rejected: List<String>)
