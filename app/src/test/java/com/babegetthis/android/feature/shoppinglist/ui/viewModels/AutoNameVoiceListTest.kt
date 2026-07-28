package com.babegetthis.android.feature.shoppinglist.ui.viewModels

import com.babegetthis.android.core.voice.model.ItemDraft
import org.junit.Assert.assertEquals
import org.junit.Test

// Covers autoNameVoiceList(): single / multiple / empty / blank / over-long.
class AutoNameVoiceListTest {

    private fun draft(name: String) = ItemDraft(name = name)

    @Test
    fun `single item uses its name`() {
        assertEquals("Milk", autoNameVoiceList(listOf(draft("Milk"))))
    }

    @Test
    fun `multiple items append plus-more`() {
        val name = autoNameVoiceList(listOf(draft("Milk"), draft("Eggs"), draft("Bread")))
        assertEquals("Milk + 2 more", name)
    }

    @Test
    fun `empty list falls back to List`() {
        assertEquals("List", autoNameVoiceList(emptyList()))
    }

    @Test
    fun `blank first name falls back to List`() {
        assertEquals("List", autoNameVoiceList(listOf(draft("   "))))
    }

    @Test
    fun `over-long first name is capped at 40 chars`() {
        val longName = "x".repeat(100)
        val name = autoNameVoiceList(listOf(draft(longName), draft("Eggs")))
        assertEquals("x".repeat(40) + " + 1 more", name)
    }

    // Exactly at the cap — the off-by-one either side of take(40).
    @Test
    fun `exactly forty chars is kept whole`() {
        val name = "x".repeat(40)
        assertEquals(name, autoNameVoiceList(listOf(draft(name))))
    }

    @Test
    fun `forty-one chars loses exactly one`() {
        assertEquals("x".repeat(40), autoNameVoiceList(listOf(draft("x".repeat(41)))))
    }

    // Trimming happens before the cap, so padding doesn't eat into the 40.
    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Milk", autoNameVoiceList(listOf(draft("  Milk  "))))
    }

    // A blank first item still counts toward "+ N more" — the fallback replaces
    // the name only, it doesn't drop the item from the tally.
    @Test
    fun `blank first name with others still counts them`() {
        val name = autoNameVoiceList(listOf(draft(""), draft("Eggs"), draft("Bread")))
        assertEquals("List + 2 more", name)
    }

    @Test
    fun `two items read as plus one more`() {
        assertEquals("Milk + 1 more", autoNameVoiceList(listOf(draft("Milk"), draft("Eggs"))))
    }
}
