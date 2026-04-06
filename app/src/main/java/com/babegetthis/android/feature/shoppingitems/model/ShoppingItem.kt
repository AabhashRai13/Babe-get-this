package com.babegetthis.android.feature.shoppingitems.model

data class ShoppingItem(
    val id: String,
    val listId: String,
    val name: String,
    val quantity: String,
    val isPickedUp: Boolean = false,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val shop: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
