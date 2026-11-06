package com.babegetthis.android.core.sync.data.local

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for real SharedPreferences: a sync point that does not survive
// the store being recreated would make every cold start a full pull.
@RunWith(RobolectricTestRunner::class)
class PrefsSyncPointStoreTest {

    private fun store() = PrefsSyncPointStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `a list with no mark reads as null`() {
        assertNull(store().get("list-1"))
    }

    @Test
    fun `a mark survives a new instance`() {
        store().set("list-1", "2026-11-06T10:00:00Z")

        assertEquals("2026-11-06T10:00:00Z", store().get("list-1"))
    }

    @Test
    fun `remove drops one list's mark and leaves the others`() {
        val store = store()
        store.set("list-1", "2026-11-06T10:00:00Z")
        store.set("list-2", "2026-11-06T11:00:00Z")

        store.remove("list-1")

        assertNull(store.get("list-1"))
        assertEquals("2026-11-06T11:00:00Z", store.get("list-2"))
    }

    @Test
    fun `clear drops every mark`() {
        val store = store()
        store.set("list-1", "2026-11-06T10:00:00Z")
        store.set("list-2", "2026-11-06T11:00:00Z")

        store.clear()

        assertNull(store.get("list-1"))
        assertNull(store.get("list-2"))
    }
}
