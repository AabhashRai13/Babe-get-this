package com.babegetthis.android.core.voice.data.repository

import com.babegetthis.android.core.voice.data.remote.dto.TranscribeItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Covers the quantity + unit flatten in TranscribeItemDto.toItemDraft().
class ToItemDraftTest {

    @Test
    fun `flattens quantity and unit into one string`() {
        val draft = TranscribeItemDto(name = "Milk", quantity = 2, unit = "bottles").toItemDraft()
        assertEquals("2 bottles", draft.quantity)
    }

    @Test
    fun `quantity with no unit is just the number`() {
        val draft = TranscribeItemDto(name = "Eggs", quantity = 12, unit = null).toItemDraft()
        assertEquals("12", draft.quantity)
    }

    @Test
    fun `no quantity and no unit becomes null`() {
        val draft = TranscribeItemDto(name = "Bread", quantity = null, unit = null).toItemDraft()
        assertNull(draft.quantity)
    }

    @Test
    fun `unit with no quantity falls back to just the unit`() {
        val draft = TranscribeItemDto(name = "Water", quantity = null, unit = "bottles").toItemDraft()
        assertEquals("bottles", draft.quantity)
    }

    @Test
    fun `carries name, category and note through verbatim`() {
        val draft = TranscribeItemDto(
            name = "Milk",
            category = "cat-dairy-eggs",
            note = "the cold one",
        ).toItemDraft()
        assertEquals("Milk", draft.name)
        assertEquals("cat-dairy-eggs", draft.category)
        assertEquals("the cold one", draft.note)
    }
}
