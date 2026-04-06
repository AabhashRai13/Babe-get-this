package com.babegetthis.android.feature.shoppingitems.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId")]
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val quantity: String,
    val isPickedUp: Boolean = false,
    val categoryId: String? = null,
    // Nullable — user can optionally assign a shop (e.g. "Whole Foods", "Costco")
    val shop: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
