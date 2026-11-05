package com.babegetthis.android.core.reminder.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPrefsReminderPrefsTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPrefsReminderPrefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rawPrefs().edit().clear().commit()
        prefs = SharedPrefsReminderPrefs(context)
    }

    private fun rawPrefs() = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    @Test
    fun `defaults are enabled, counter 0, no pending, no snooze, not requested`() {
        assertTrue(prefs.enabled)
        assertEquals(0L, prefs.generationCounter)
        assertNull(prefs.readPending())
        assertEquals(0L, prefs.promptSnoozedUntil)
        assertFalse(prefs.permissionRequested)
    }

    @Test
    fun `commitPending stores the record and advances the counter`() {
        val record = PendingReminder(listId = "list-1", generation = 7L, armedAt = 1_000L)

        assertTrue(prefs.commitPending(record))

        assertEquals(record, prefs.readPending())
        assertEquals(7L, prefs.generationCounter)
        // A second instance over the same file sees the durable record.
        assertEquals(record, SharedPrefsReminderPrefs(context).readPending())
    }

    @Test
    fun `commitClearPending removes the record but keeps the counter`() {
        prefs.commitPending(PendingReminder("list-1", 7L, 1_000L))

        assertTrue(prefs.commitClearPending())

        assertNull(prefs.readPending())
        assertEquals(7L, prefs.generationCounter)
    }

    @Test
    fun `a record with a missing field reads as null`() {
        rawPrefs().edit().putString("pending_list_id", "list-1").commit()
        assertNull(prefs.readPending())

        rawPrefs().edit().putLong("pending_generation", 3L).commit()
        assertNull(prefs.readPending())

        rawPrefs().edit().remove("pending_list_id").putLong("pending_armed_at", 5L).commit()
        assertNull(prefs.readPending())

        rawPrefs().edit().remove("pending_generation").putString("pending_list_id", "list-1").commit()
        assertNull(prefs.readPending())

        rawPrefs().edit().putLong("pending_generation", 3L).commit()
        assertEquals(PendingReminder("list-1", 3L, 5L), prefs.readPending())
    }

    @Test
    fun `enabled, snooze and permissionRequested round-trip`() {
        prefs.enabled = false
        prefs.promptSnoozedUntil = 12_345L
        prefs.permissionRequested = true

        val reopened = SharedPrefsReminderPrefs(context)
        assertFalse(reopened.enabled)
        assertEquals(12_345L, reopened.promptSnoozedUntil)
        assertTrue(reopened.permissionRequested)

        prefs.enabled = true
        assertTrue(SharedPrefsReminderPrefs(context).enabled)
    }
}
