package com.babegetthis.android.core.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
