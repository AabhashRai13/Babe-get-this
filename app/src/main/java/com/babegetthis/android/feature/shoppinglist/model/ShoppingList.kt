package com.babegetthis.android.feature.shoppinglist.model

data class ShoppingList(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    // Not stored in the entity — calculated from a query count
    val itemCount: Int = 0,
)
