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

    // The backend field is "location"; the app calls it shop.
    @Test
    fun `location becomes shop`() {
        assertEquals(
            "Dan Murphy's",
            TranscribeItemDto(name = "Wine", location = "Dan Murphy's").toItemDraft().shop,
        )
    }

    @Test
    fun `absent optional fields stay null`() {
        val draft = TranscribeItemDto(name = "Bread").toItemDraft()

        assertNull(draft.category)
        assertNull(draft.note)
        assertNull(draft.shop)
        assertNull(draft.quantity)
    }

    // ifBlank collapses a whitespace-only unit rather than storing " ".
    @Test
    fun `a blank unit with no quantity collapses to null`() {
        assertNull(TranscribeItemDto(name = "Water", quantity = null, unit = "   ").toItemDraft().quantity)
    }

    @Test
    fun `a zero quantity is kept rather than treated as absent`() {
        assertEquals("0", TranscribeItemDto(name = "Milk", quantity = 0).toItemDraft().quantity)
    }

    @Test
    fun `a negative quantity is carried through as-is`() {
        // Nothing validates this today; pinned so a future guard is a deliberate
        // change rather than an accident.
        assertEquals("-1", TranscribeItemDto(name = "Milk", quantity = -1).toItemDraft().quantity)
    }

    @Test
    fun `an empty name is carried through`() {
        // The repository, not the mapper, is where blank names are rejected.
        assertEquals("", TranscribeItemDto(name = "").toItemDraft().name)
    }
}
