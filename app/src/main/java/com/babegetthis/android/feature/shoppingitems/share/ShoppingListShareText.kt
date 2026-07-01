package com.babegetthis.android.feature.shoppingitems.share

import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem

/**
 * Builds the plain-text representation of a list for sharing via ACTION_SEND.
 * Pure (name, items) -> String, no Android types, so it unit-tests without mocks.
 */
object ShoppingListShareText {

    private const val FOOTER = "via Babe, Get This"

    fun format(listName: String, items: List<ShoppingItem>): String {
        val unchecked = items.filterNot { it.isPickedUp }
        // All picked up -> share the whole list rather than an empty message.
        val toList = unchecked.ifEmpty { items }
        val pickedUpCount = if (unchecked.isEmpty()) 0 else items.size - unchecked.size

        return buildString {
            append("🛒 ").append(listName).append("\n\n")
            toList.forEach { item ->
                append("[ ] ").append(item.name)
                if (item.quantity.isNotBlank()) append(" — ").append(item.quantity)
                append("\n")
            }
            if (pickedUpCount > 0) {
                append("\n(").append(pickedUpCount).append(" already picked up)\n")
            }
            append("\n").append(FOOTER)
        }
    }
}
