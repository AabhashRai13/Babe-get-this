package com.babegetthis.android.core.reminder

import android.content.Intent
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // Relaunching from Recents re-delivers the task's base intent, which still
    // carries the extras of a tap that was already handled.
    @Test
    fun `a relaunch from Recents ignores the reminder extras and tracks nothing`() {
        val tracked = mutableListOf<AnalyticsEvent>()
        val fromHistory = reminderIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        val target = ReminderIntentParser.handleReminderIntent(
            fromHistory,
            startAtWelcome = false,
            restored = false,
        ) { tracked.add(it) }

        assertNull(target)
        assertEquals(emptyList<AnalyticsEvent>(), tracked)
    }

    private fun gotThisIntent(listId: String? = "list-1", createList: Boolean = false) = Intent().apply {
        putExtra(ReminderIntentParser.EXTRA_SOURCE, "got_this")
        if (listId != null) {
            putExtra(ReminderIntentParser.EXTRA_LIST_ID, listId)
            putExtra(ReminderIntentParser.EXTRA_LIST_NAME, "Weekly shop")
        }
        if (createList) putExtra(ReminderIntentParser.EXTRA_CREATE_LIST, true)
    }

    @Test
    fun `the Got This extra keys are stable`() {
        assertEquals("reminder_source", ReminderIntentParser.EXTRA_SOURCE)
        assertEquals("reminder_create_list", ReminderIntentParser.EXTRA_CREATE_LIST)
    }

    @Test
    fun `a Got This tap with a list tracks GotThisReminderOpened, not ReminderTapped`() {
        val tracked = mutableListOf<AnalyticsEvent>()

        val target = ReminderIntentParser.handleReminderIntent(gotThisIntent(), false, false, { tracked.add(it) })

        assertEquals(ReminderTarget("list-1", "Weekly shop"), target)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.GotThisReminderOpened), tracked)
    }

    @Test
    fun `a Got This tap that asks for a new list tracks GotThisReminderOpened once and has no target`() {
        val tracked = mutableListOf<AnalyticsEvent>()
        val intent = gotThisIntent(listId = null, createList = true)

        val target = ReminderIntentParser.handleReminderIntent(intent, false, false, { tracked.add(it) })

        assertNull(target)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.GotThisReminderOpened), tracked)
    }

    @Test
    fun `a Got This tap on a restored activity, from Recents or at Welcome tracks nothing`() {
        val tracked = mutableListOf<AnalyticsEvent>()
        val intent = gotThisIntent(listId = null, createList = true)

        ReminderIntentParser.handleReminderIntent(intent, false, true, { tracked.add(it) })
        ReminderIntentParser.handleReminderIntent(
            Intent(intent).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY),
            false,
            false,
            { tracked.add(it) },
        )
        ReminderIntentParser.handleReminderIntent(intent, true, false, { tracked.add(it) })
        ReminderIntentParser.handleReminderIntent(Intent(), false, false, { tracked.add(it) })

        assertEquals(emptyList<AnalyticsEvent>(), tracked)
    }

    @Test
    fun `wantsCreateList is true only for a Got This intent asking for it`() {
        assertTrue(ReminderIntentParser.wantsCreateList(gotThisIntent(listId = null, createList = true)))
        assertFalse(ReminderIntentParser.wantsCreateList(gotThisIntent()))
        assertFalse(
            ReminderIntentParser.wantsCreateList(Intent().putExtra(ReminderIntentParser.EXTRA_CREATE_LIST, true)),
        )
        assertFalse(ReminderIntentParser.wantsCreateList(null))
    }

    @Test
    fun `handleCreateListIntent is true only for a fresh Got This create-list tap`() {
        val fresh = gotThisIntent(listId = null, createList = true)
        assertTrue(ReminderIntentParser.handleCreateListIntent(fresh, startAtWelcome = false, restored = false))

        assertFalse(ReminderIntentParser.handleCreateListIntent(fresh, startAtWelcome = false, restored = true))
        assertFalse(ReminderIntentParser.handleCreateListIntent(fresh, startAtWelcome = true, restored = false))
        assertFalse(
            ReminderIntentParser.handleCreateListIntent(
                Intent(fresh).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY),
                startAtWelcome = false,
                restored = false,
            ),
        )
        assertFalse(ReminderIntentParser.handleCreateListIntent(reminderIntent(), false, false))
        assertFalse(ReminderIntentParser.handleCreateListIntent(null, false, false))
    }
}
