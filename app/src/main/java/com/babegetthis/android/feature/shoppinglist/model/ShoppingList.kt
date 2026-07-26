package com.babegetthis.android.feature.shoppinglist.model

data class ShoppingList(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocked: Boolean = false,
    // Not stored in the entity — calculated from a query count
    val itemCount: Int = 0,
    val completedItemCount: Int = 0,
) {
    // A list is "completed" when it has items and all of them are picked up.
    // Empty lists are NOT completed — they're still active (waiting for items).
    val isCompleted: Boolean
        get() = itemCount > 0 && completedItemCount == itemCount
}
