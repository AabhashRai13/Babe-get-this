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
    // every existing list stays accessible after upgrade.
    val isLocked: Boolean = false,
)
