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
}
