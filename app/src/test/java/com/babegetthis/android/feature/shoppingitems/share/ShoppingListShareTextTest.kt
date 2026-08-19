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

    // Sharing an empty message would be worse, so a fully-completed list shares
    // everything. Side effect worth knowing: those items still render as "[ ]",
    // so the shared text can't be told apart from an untouched list. Deliberate
    // (see the inline comment in format()), pinned here so it stays deliberate.
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

    @Test
    fun emptyListStillCarriesTitleAndFooter() {
        val text = ShoppingListShareText.format("Groceries", emptyList())

        assertTrue(text.contains("🛒 Groceries"))
        assertTrue(text.contains("via Babe, Get This"))
        assertFalse(text.contains("[ ]"))
    }

    @Test
    fun noneCheckedOmitsTheSummaryLine() {
        val text = ShoppingListShareText.format("L", listOf(item("Milk"), item("Eggs")))

        assertTrue(text.contains("[ ] Milk"))
        assertTrue(text.contains("[ ] Eggs"))
        assertFalse(text.contains("already picked up"))
    }

    @Test
    fun summaryCountsEveryCheckedItem() {
        val text = ShoppingListShareText.format(
            "L",
            listOf(
                item("Milk"),
                item("Eggs", pickedUp = true),
                item("Bread", pickedUp = true),
                item("Jam", pickedUp = true),
            ),
        )

        assertTrue(text.contains("(3 already picked up)"))
    }

    @Test
    fun whitespaceQuantityIsTreatedAsAbsent() {
        val text = ShoppingListShareText.format("L", listOf(item("Bread", quantity = "   ")))

        assertFalse(text.contains("—"))
    }

    @Test
    fun theListNameIsUsedVerbatim() {
        val text = ShoppingListShareText.format("Mum's list — 2024", listOf(item("Milk")))

        assertTrue(text.contains("🛒 Mum's list — 2024"))
    }

    @Test
    fun aVeryLongItemNameIsNotTruncated() {
        val long = "x".repeat(200)
        val text = ShoppingListShareText.format("L", listOf(item(long)))

        assertTrue(text.contains("[ ] $long"))
    }
}
