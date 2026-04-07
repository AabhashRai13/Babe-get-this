package com.babegetthis.android.feature.shoppingitems.data.mapper

import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem

fun ShoppingItemEntity.toDomain(categoryName: String? = null): ShoppingItem =
    ShoppingItem(
        id = id,
        listId = listId,
        name = name,
        quantity = quantity,
        isPickedUp = isPickedUp,
        categoryId = categoryId,
        categoryName = categoryName,
        shop = shop,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ShoppingItem.toEntity(): ShoppingItemEntity =
    ShoppingItemEntity(
        id = id,
        listId = listId,
        name = name,
        quantity = quantity,
        isPickedUp = isPickedUp,
        categoryId = categoryId,
        shop = shop,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
