package com.babegetthis.android.testing

import com.babegetthis.android.core.data.local.model.CategoryEntity
import com.babegetthis.android.core.model.Category
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.feature.shoppinglist.model.ShoppingList

// Builders with sensible defaults, so a test names only the fields it is actually
// asserting on. Timestamps are fixed constants rather than System.currentTimeMillis()
// — a test that reads the wall clock asserts something different depending on the
// day it runs, which is the bug DateGroupingTest exists to prevent.
object TestData {

    // 2024-06-01T00:00:00Z. Arbitrary but fixed; tests that care about time
    // derive from this rather than from "now".
    const val T0 = 1_717_200_000_000L
    const val DAY = 24 * 60 * 60 * 1000L

    fun listEntity(
        id: String = "list-1",
        name: String = "Groceries",
        createdAt: Long = T0,
        updatedAt: Long = T0,
        isLocked: Boolean = false,
    ) = ShoppingListEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isLocked = isLocked,
    )

    fun list(
        id: String = "list-1",
        name: String = "Groceries",
        createdAt: Long = T0,
        updatedAt: Long = T0,
        isLocked: Boolean = false,
        itemCount: Int = 0,
        completedItemCount: Int = 0,
    ) = ShoppingList(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isLocked = isLocked,
        itemCount = itemCount,
        completedItemCount = completedItemCount,
    )

    fun itemEntity(
        id: String = "item-1",
        listId: String = "list-1",
        name: String = "Milk",
        quantity: String = "1",
        isPickedUp: Boolean = false,
        categoryId: String? = null,
        shop: String? = null,
        note: String? = null,
        createdAt: Long = T0,
        updatedAt: Long = T0,
    ) = ShoppingItemEntity(
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

    fun item(
        id: String = "item-1",
        listId: String = "list-1",
        name: String = "Milk",
        quantity: String = "1",
        isPickedUp: Boolean = false,
        categoryId: String? = null,
        categoryName: String? = null,
        note: String? = null,
        shop: String? = null,
        createdAt: Long = T0,
        updatedAt: Long = T0,
    ) = ShoppingItem(
        id = id,
        listId = listId,
        name = name,
        quantity = quantity,
        isPickedUp = isPickedUp,
        categoryId = categoryId,
        categoryName = categoryName,
        note = note,
        shop = shop,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun categoryEntity(
        id: String = "cat-1",
        name: String = "Dairy",
        isDefault: Boolean = true,
    ) = CategoryEntity(id = id, name = name, isDefault = isDefault)

    fun category(
        id: String = "cat-1",
        name: String = "Dairy",
        isDefault: Boolean = true,
    ) = Category(id = id, name = name, isDefault = isDefault)

    fun draft(
        name: String = "Milk",
        quantity: String? = null,
        note: String? = null,
        category: String? = null,
        shop: String? = null,
    ) = ItemDraft(
        name = name,
        quantity = quantity,
        note = note,
        category = category,
        shop = shop,
    )
}
