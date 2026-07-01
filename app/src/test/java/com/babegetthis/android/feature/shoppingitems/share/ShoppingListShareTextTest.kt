package com.babegetthis.android.feature.shoppingitems.share

import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListShareTextTest {

    private fun item(name: String, quantity: String = "", pickedUp: Boolean = false) =
        ShoppingItem(
            id = name, listId = "l", name = name, quantity = quantity,
            isPickedUp = pickedUp, createdAt = 0, updatedAt = 0,
        )

    @Test
    fun dropsCheckedAndAddsSummary() {
        val text = ShoppingListShareText.format(
            "Groceries",
            listOf(item("Milk", "2kg"), item("Eggs"), item("Coffee", pickedUp = true)),
        )
        assertTrue(text.contains("[ ] Milk — 2kg"))
        assertTrue(text.contains("[ ] Eggs"))
        assertFalse(text.contains("Coffee"))
        assertTrue(text.contains("(1 already picked up)"))
        assertTrue(text.contains("via Babe, Get This"))
    }

    @Test
    fun blankQuantityShowsNameOnly() {
        val text = ShoppingListShareText.format("L", listOf(item("Bread")))
        assertTrue(text.contains("[ ] Bread\n"))
        assertFalse(text.contains("—"))
    }

    @Test
    fun allCheckedSharesWholeListWithoutSummary() {
        val text = ShoppingListShareText.format(
            "L",
            listOf(item("Milk", pickedUp = true), item("Eggs", pickedUp = true)),
        )
        assertTrue(text.contains("[ ] Milk"))
        assertTrue(text.contains("[ ] Eggs"))
        assertFalse(text.contains("already picked up"))
    }
}
