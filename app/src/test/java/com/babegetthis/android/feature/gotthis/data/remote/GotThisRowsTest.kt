package com.babegetthis.android.feature.gotthis.data.remote

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

// The JSON the phone sends. record_ticks rejects an event missing any of its
// seven keys, so the shape is pinned here while the RPC contract is unrun.
class GotThisRowsTest {

    @Test
    fun `a tick event encodes to exactly its seven keys, nulls included`() {
        val event = TickEventRow(
            id = "tick-1",
            itemId = "item-1",
            listId = "list-1",
            tickedAt = "2026-11-01T10:00:00.000Z",
            name = "Milk",
            categoryId = null,
            undoneAt = null,
        )

        val encoded = encodeTickEvents(listOf(event)).jsonArray.single().jsonObject

        assertEquals(
            setOf("id", "item_id", "list_id", "ticked_at", "name", "category_id", "undone_at"),
            encoded.keys,
        )
        assertEquals(JsonNull, encoded["category_id"])
        assertEquals(JsonNull, encoded["undone_at"])
        assertEquals(JsonPrimitive("item-1"), encoded["item_id"])
        assertEquals(JsonPrimitive("2026-11-01T10:00:00.000Z"), encoded["ticked_at"])
    }

    @Test
    fun `an own purchase encodes to exactly its five keys`() {
        val row = OwnPurchaseRow(
            tickId = "tick-1",
            sourceItemId = "item-1",
            name = "Milk",
            categoryId = null,
            boughtAt = "2026-11-01T10:00:00.000Z",
        )

        val encoded = encodeOwnPurchases(listOf(row)).jsonArray.single().jsonObject

        assertEquals(setOf("tick_id", "source_item_id", "name", "category_id", "bought_at"), encoded.keys)
        assertEquals(JsonNull, encoded["category_id"])
        assertEquals(JsonPrimitive("item-1"), encoded["source_item_id"])
    }
}
