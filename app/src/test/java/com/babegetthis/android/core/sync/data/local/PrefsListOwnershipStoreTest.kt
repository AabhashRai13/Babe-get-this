package com.babegetthis.android.core.sync.data.local

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for real SharedPreferences. Everything here exists to survive
// sign-out eviction and process death, so each test reads back through a NEW
// instance: an in-memory copy that forgets on restart would pass a weaker test
// and fail the user.
@RunWith(RobolectricTestRunner::class)
class PrefsListOwnershipStoreTest {

    private fun store() = PrefsListOwnershipStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `the adoption notice starts at zero and is observable`() {
        val store = store()
        assertEquals(0, store.adoptionNotice.value)

        store.setAdoptionNotice(3)

        assertEquals(3, store.adoptionNotice.value)
    }

    @Test
    fun `the adoption notice survives a new instance`() {
        store().setAdoptionNotice(2)

        assertEquals(2, store().adoptionNotice.value)
    }

    @Test
    fun `the migration mark is off until set and then sticks`() {
        assertFalse(store().isAccountListsMigrated())

        store().markAccountListsMigrated()

        assertTrue(store().isAccountListsMigrated())
    }

    @Test
    fun `locks are remembered per list and survive a new instance`() {
        val store = store()
        store.rememberLock("l1", true)
        store.rememberLock("l2", true)

        assertEquals(setOf("l1", "l2"), store().lockedListIds())
    }

    @Test
    fun `unlocking forgets just that list`() {
        val store = store()
        store.rememberLock("l1", true)
        store.rememberLock("l2", true)

        store.rememberLock("l1", false)

        assertEquals(setOf("l2"), store.lockedListIds())
    }

    @Test
    fun `forgetAllLocks clears every remembered lock`() {
        val store = store()
        store.rememberLock("l1", true)

        store.forgetAllLocks()

        assertTrue(store().lockedListIds().isEmpty())
    }

    @Test
    fun `unverified ids are empty until set and are replaced wholesale`() {
        val store = store()
        assertTrue(store.unverifiedListIds().isEmpty())

        store.setUnverifiedListIds(setOf("a", "b"))
        store.setUnverifiedListIds(setOf("c"))

        assertEquals(setOf("c"), store().unverifiedListIds())
    }
}
