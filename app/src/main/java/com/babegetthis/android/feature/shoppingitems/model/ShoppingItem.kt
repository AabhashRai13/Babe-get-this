package com.babegetthis.android.feature.shoppingitems.model

data class ShoppingItem(
    val id: String,
    val listId: String,
    val name: String,
    val quantity: String,
    val isPickedUp: Boolean = false,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val note: String? = null,
    val shop: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    // The current or most recent tick time; every full-row write carries it
    // back, or an edit would make a ticked item look never ticked.
    val pickedUpAt: Long? = null,
)
