package com.babegetthis.android.feature.shoppingitems.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity

// ForeignKey = like a SQL foreign key constraint.
// If a shopping list is deleted, all its items get deleted too (CASCADE).
// Index on listId = faster queries when fetching items for a specific list.

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
    // Nullable — items don't have to belong to a category
    val categoryId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
