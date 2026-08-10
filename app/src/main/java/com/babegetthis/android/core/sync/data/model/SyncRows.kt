package com.babegetthis.android.core.sync.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shapes of the Supabase `lists` / `items` rows (see
// supabase/migrations/001_realtime_list_sharing.sql). Row ids are the same
// client-generated UUIDs Room uses, so there is no id mapping anywhere.
//
// `updatedAt` is null on push on purpose: the server trigger stamps it, and a
// client-supplied value would be overwritten anyway — phones never own the
// LWW clock. It is always present on rows read back from the server.

@Serializable
data class ListRow(
    val id: String,
    val name: String,
    @SerialName("share_code") val shareCode: String,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class ItemRow(
    val id: String,
    @SerialName("list_id") val listId: String,
    val name: String,
    val quantity: String,
    @SerialName("is_picked_up") val isPickedUp: Boolean = false,
    @SerialName("category_id") val categoryId: String? = null,
    val shop: String? = null,
    val note: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)
