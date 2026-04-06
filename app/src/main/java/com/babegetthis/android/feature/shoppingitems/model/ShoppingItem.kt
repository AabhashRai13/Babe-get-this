package com.babegetthis.android.feature.shoppingitems.model

data class ShoppingItem(
    val id: String,
    val listId: String,
    val name: String,
    val quantity: String,
    val isPickedUp: Boolean = false,
    val categoryId: String? = null,
    // Resolved from the category table — so the UI can display the name
    val categoryName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
