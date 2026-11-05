package com.babegetthis.android.core.reminder

/** The list a reminder tap should open. */
data class ReminderTarget(val listId: String, val listName: String)

/**
 * Intent-extra contract between [ReminderNotifier], which writes the extras into
 * the notification's content intent, and the navigation layer, which reads them.
 */
object ReminderIntentParser {
    const val EXTRA_LIST_ID = "reminder_list_id"
    const val EXTRA_LIST_NAME = "reminder_list_name"
}
