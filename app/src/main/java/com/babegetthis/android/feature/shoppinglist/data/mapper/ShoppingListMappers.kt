package com.babegetthis.android.feature.shoppinglist.data.mapper

import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListWithItemCount
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList

fun ShoppingListWithItemCount.toDomain(): ShoppingList =
    ShoppingList(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        itemCount = itemCount,
    )

fun ShoppingListEntity.toDomain(): ShoppingList =
    ShoppingList(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ShoppingList.toEntity(): ShoppingListEntity =
    ShoppingListEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
