package com.babegetthis.android.core.reminder

import android.content.Intent
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderIntentParserTest {

    @Test
    fun `a ReminderTarget carries the list id and name`() {
        val target = ReminderTarget(listId = "list-1", listName = "Weekly shop")

        assertEquals("list-1", target.listId)
        assertEquals("Weekly shop", target.listName)
        assertEquals(ReminderTarget("list-1", "Weekly shop"), target)
    }

    @Test
    fun `the extra keys are stable`() {
        // Written into a PendingIntent that may outlive the process; renaming
        // them would orphan notifications already posted.
        assertEquals("reminder_list_id", ReminderIntentParser.EXTRA_LIST_ID)
        assertEquals("reminder_list_name", ReminderIntentParser.EXTRA_LIST_NAME)
    }

    private fun reminderIntent(listId: String? = "list-1", listName: String? = "Weekly shop") = Intent().apply {
        if (listId != null) putExtra(ReminderIntentParser.EXTRA_LIST_ID, listId)
        if (listName != null) putExtra(ReminderIntentParser.EXTRA_LIST_NAME, listName)
    }

    @Test
    fun `both extras present yields a target`() {
        assertEquals(
            ReminderTarget("list-1", "Weekly shop"),
            ReminderIntentParser.parse(reminderIntent(), startAtWelcome = false),
        )
    }

    @Test
    fun `a missing or blank extra yields null`() {
        assertNull(ReminderIntentParser.parse(reminderIntent(listId = null), startAtWelcome = false))
        assertNull(ReminderIntentParser.parse(reminderIntent(listName = null), startAtWelcome = false))
        assertNull(ReminderIntentParser.parse(reminderIntent(listId = "  "), startAtWelcome = false))
        assertNull(ReminderIntentParser.parse(reminderIntent(listName = ""), startAtWelcome = false))
    }

    @Test
    fun `startAtWelcome yields null even with extras`() {
        assertNull(ReminderIntentParser.parse(reminderIntent(), startAtWelcome = true))
    }

    @Test
    fun `a null intent yields null`() {
        assertNull(ReminderIntentParser.parse(null, startAtWelcome = false))
    }

    @Test
    fun `tracks ReminderTapped once when a target parses, never otherwise`() {
        val tracked = mutableListOf<AnalyticsEvent>()

        val target = ReminderIntentParser.handleReminderIntent(reminderIntent(), false, false, { tracked.add(it) })
        assertEquals(ReminderTarget("list-1", "Weekly shop"), target)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ReminderTapped), tracked)

        tracked.clear()
        assertNull(
            ReminderIntentParser.handleReminderIntent(reminderIntent(listId = null), false, false, {
                tracked.add(it)
            }),
        )
        assertNull(ReminderIntentParser.handleReminderIntent(reminderIntent(), true, false, { tracked.add(it) }))
        assertNull(ReminderIntentParser.handleReminderIntent(null, false, false, { tracked.add(it) }))
        assertEquals(emptyList<AnalyticsEvent>(), tracked)
    }

    @Test
    fun `a restored activity ignores the launch intent and tracks nothing`() {
        val tracked = mutableListOf<AnalyticsEvent>()

        val target = ReminderIntentParser.handleReminderIntent(
            reminderIntent(),
            startAtWelcome = false,
            restored = true,
        ) { tracked.add(it) }

        assertNull(target)
        assertEquals(emptyList<AnalyticsEvent>(), tracked)
    }
}
