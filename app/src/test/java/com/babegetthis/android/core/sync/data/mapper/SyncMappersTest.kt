package com.babegetthis.android.core.sync.data.mapper

import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMappersTest {

    private val iso = "2026-08-01T10:00:00Z"
    private val ms = 1785578400000L // == iso

    private fun listEntity(
        shareCode: String? = "ABC123",
        deletedAt: Long? = null,
    ) = ShoppingListEntity(
        id = "list-1", name = "Groceries", createdAt = 1L, updatedAt = 2L,
        isLocked = true, shareCode = shareCode, deletedAt = deletedAt, pendingSync = true,
    )

    private fun itemEntity(deletedAt: Long? = null) = ShoppingItemEntity(
        id = "item-1", listId = "list-1", name = "Milk", quantity = "2 bags",
        isPickedUp = true, categoryId = "cat-1", shop = "Costco", note = "the blue one",
        createdAt = 1L, updatedAt = 2L, deletedAt = deletedAt, pendingSync = true,
    )

    // --- iso helpers ---

    @Test
    fun `iso round-trips through millis`() {
        assertEquals(ms, iso.isoToMillis())
        assertEquals(ms, ms.millisToIso().isoToMillis())
    }

    @Test
    fun `iso with explicit offset parses`() {
        assertEquals(ms, "2026-08-01T10:00:00+00:00".isoToMillis())
    }

    // --- entity → row (push direction) ---

    @Test
    fun `list toRow carries code and creator and leaves the clock to the server`() {
        val row = listEntity().toRow(createdBy = "user-9")

        assertEquals("list-1", row.id)
        assertEquals("ABC123", row.shareCode)
        assertEquals("user-9", row.createdBy)
        assertNull("server owns updated_at", row.updatedAt)
        assertNull(row.deletedAt)
    }

    @Test
    fun `list toRow refuses a local-only list`() {
        assertThrows(IllegalArgumentException::class.java) {
            listEntity(shareCode = null).toRow(createdBy = "user-9")
        }
    }

    @Test
    fun `tombstones travel as ISO deleted_at`() {
        assertEquals(iso, listEntity(deletedAt = ms).toRow("u").deletedAt)
        assertEquals(iso, itemEntity(deletedAt = ms).toRow().deletedAt)
    }

    @Test
    fun `item toRow copies every synced field`() {
        val row = itemEntity().toRow()

        assertEquals(
            ItemRow(
                id = "item-1", listId = "list-1", name = "Milk", quantity = "2 bags",
                isPickedUp = true, categoryId = "cat-1", shop = "Costco",
                note = "the blue one", updatedAt = null, deletedAt = null,
            ),
            row,
        )
    }

    // --- row → entity (apply direction) ---

    @Test
    fun `new list row lands clean with the server clock`() {
        val entity = ListRow(
            id = "list-1", name = "Groceries", shareCode = "ABC123", updatedAt = iso,
        ).toEntity(local = null)

        assertEquals(ms, entity.updatedAt)
        assertEquals("no local row → server time becomes createdAt", ms, entity.createdAt)
        assertFalse(entity.isLocked)
        assertFalse(entity.pendingSync)
        assertEquals("ABC123", entity.shareCode)
    }

    @Test
    fun `existing local list keeps device-local concerns`() {
        val entity = ListRow(
            id = "list-1", name = "Renamed", shareCode = "ABC123", updatedAt = iso,
        ).toEntity(local = listEntity())

        assertEquals("Renamed", entity.name)
        assertEquals("createdAt preserved", 1L, entity.createdAt)
        assertTrue("PIN lock preserved", entity.isLocked)
        assertFalse("applied rows are clean", entity.pendingSync)
    }

    @Test
    fun `incoming tombstone becomes a local tombstone`() {
        val entity = ItemRow(
            id = "item-1", listId = "list-1", name = "Milk", quantity = "1",
            updatedAt = iso, deletedAt = iso,
        ).toEntity(local = itemEntity())

        assertEquals(ms, entity.deletedAt)
    }

    @Test
    fun `item row without server clock is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemRow(id = "i", listId = "l", name = "n", quantity = "1", updatedAt = null)
                .toEntity(local = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ListRow(id = "l", name = "n", shareCode = "c", updatedAt = null)
                .toEntity(local = null)
        }
    }
}
