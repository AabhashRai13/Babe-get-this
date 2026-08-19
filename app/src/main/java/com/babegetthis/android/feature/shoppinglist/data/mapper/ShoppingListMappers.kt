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
        isLocked = isLocked,
        itemCount = itemCount,
        completedItemCount = completedItemCount,
    )

// NOTE: itemCount / completedItemCount are not on the entity, so they come back
// as 0 here — which means the derived `isCompleted` is always false on anything
// built through this mapper. Fine for the only current caller (getListById, used
// for the lock flag), but read completion state off ShoppingListWithItemCount
// above, never off this.
fun ShoppingListEntity.toDomain(): ShoppingList =
    ShoppingList(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isLocked = isLocked,
    )

fun ShoppingList.toEntity(): ShoppingListEntity =
    ShoppingListEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isLocked = isLocked,
    )
