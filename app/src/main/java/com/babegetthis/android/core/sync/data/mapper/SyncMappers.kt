package com.babegetthis.android.core.sync.data.mapper

import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import kotlinx.datetime.Instant

// ISO-8601 timestamptz ↔ epoch millis. kotlinx-datetime (already on the
// classpath via supabase-kt) instead of java.time: minSdk is 24 and core
// library desugaring isn't enabled.
fun String.isoToMillis(): Long = Instant.parse(this).toEpochMilliseconds()
fun Long.millisToIso(): String = Instant.fromEpochMilliseconds(this).toString()

fun ShoppingListEntity.toRow(createdBy: String): ListRow = ListRow(
    id = id,
    name = name,
    shareCode = requireNotNull(shareCode) { "only shared lists are pushed" },
    createdBy = createdBy,
    deletedAt = deletedAt?.millisToIso(),
)

fun ShoppingItemEntity.toRow(): ItemRow = ItemRow(
    id = id,
    listId = listId,
    name = name,
    quantity = quantity,
    isPickedUp = isPickedUp,
    categoryId = categoryId,
    shop = shop,
    note = note,
    deletedAt = deletedAt?.millisToIso(),
)

// Incoming rows land as clean (pendingSync = false) entities whose updatedAt
// carries the SERVER clock in millis — that's what later LWW comparisons run
// against. `local` is the row currently in Room (any state, may be null):
// device-local concerns (createdAt ordering, the PIN lock) survive from it.
fun ListRow.toEntity(local: ShoppingListEntity?): ShoppingListEntity {
    val serverMs = requireNotNull(updatedAt) { "server rows always carry updated_at" }.isoToMillis()
    return ShoppingListEntity(
        id = id,
        name = name,
        createdAt = local?.createdAt ?: serverMs,
        updatedAt = serverMs,
        isLocked = local?.isLocked ?: false, // per-device, never synced
        shareCode = shareCode,
        deletedAt = deletedAt?.isoToMillis(),
        pendingSync = false,
    )
}

fun ItemRow.toEntity(local: ShoppingItemEntity?): ShoppingItemEntity {
    val serverMs = requireNotNull(updatedAt) { "server rows always carry updated_at" }.isoToMillis()
    return ShoppingItemEntity(
        id = id,
        listId = listId,
        name = name,
        quantity = quantity,
        isPickedUp = isPickedUp,
        categoryId = categoryId, // opaque; unknown ids render as uncategorised
        shop = shop,
        note = note,
        createdAt = local?.createdAt ?: serverMs,
        updatedAt = serverMs,
        deletedAt = deletedAt?.isoToMillis(),
        pendingSync = false,
    )
}
