package com.babegetthis.android.feature.gotthis.data.local.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// One tick of a shopping item, kept as history. Added in DB v6
// (MIGRATION_5_6). No foreign key: history outlives the item and its list.
@Entity(
    tableName = "purchases",
    indices = [Index("accountId", "itemKey"), Index("tickId")],
)
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val tickId: String,
    // Owner; null is a guest purchase on this phone.
    val accountId: String?,
    val sourceItemId: String,
    val name: String,
    val itemKey: String,
    val categoryId: String?,
    val boughtAt: Long,
    // Tombstone from an undo.
    val deletedAt: Long?,
    // To send with upload_own_purchases.
    val pendingUpload: Boolean,
)
