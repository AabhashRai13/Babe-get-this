package com.babegetthis.android.feature.shoppinglist.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    // Per-list lock. Contents/delete/share are gated behind the device PIN
    // when true. Added in DB v2 (MIGRATION_1_2) with a default of false so
    // every existing list stays accessible after upgrade. Never syncs: the
    // share code outranks the lock, and each device may lock its own replica.
    val isLocked: Boolean = false,
    // Sync columns, added in DB v3 (MIGRATION_2_3). Null shareCode == the list
    // is local-only and no sync machinery ever touches it.
    val shareCode: String? = null,
    // Tombstone (epoch millis). Shared lists soft-delete so the deletion can
    // sync; local-only lists keep the original hard DELETE + CASCADE path.
    val deletedAt: Long? = null,
    // Dirty flag: true = this row has local changes not yet pushed. This IS
    // the outbox — no separate queue table.
    val pendingSync: Boolean = false,
)
