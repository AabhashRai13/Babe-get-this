package com.babegetthis.android.core.reminder

import android.content.Intent
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent

/** The list a reminder tap should open. */
data class ReminderTarget(val listId: String, val listName: String)

/**
 * Intent-extra contract between [ReminderNotifier], which writes the extras into
 * the notification's content intent, and the navigation layer, which reads them.
 */
object ReminderIntentParser {
    const val EXTRA_LIST_ID = "reminder_list_id"
    const val EXTRA_LIST_NAME = "reminder_list_name"

    /**
     * The list a reminder tap should open, or null when [intent] is not a
     * reminder tap. Welcome wins: a signed-out user who has not chosen yet has
     * no catalog to land on top of.
     */
    fun parse(intent: Intent?, startAtWelcome: Boolean): ReminderTarget? {
        if (intent == null || startAtWelcome) return null
        val listId = intent.getStringExtra(EXTRA_LIST_ID)
        val listName = intent.getStringExtra(EXTRA_LIST_NAME)
        if (listId.isNullOrBlank() || listName.isNullOrBlank()) return null
        return ReminderTarget(listId, listName)
    }

    /**
     * [parse], plus one [AnalyticsEvent.ReminderTapped] when it yields a target.
     *
     * [restored] is true when the activity is being recreated (rotation, process
     * restore): `getIntent()` still holds the original reminder intent, but that
     * tap was already handled and the NavController has restored its back stack,
     * so re-parsing would double-count the tap and re-navigate. Returns null and
     * tracks nothing then.
     */
    fun handleReminderIntent(
        intent: Intent?,
        startAtWelcome: Boolean,
        restored: Boolean,
        track: (AnalyticsEvent) -> Unit,
    ): ReminderTarget? {
        if (restored) return null
        val target = parse(intent, startAtWelcome) ?: return null
        track(AnalyticsEvent.ReminderTapped)
        return target
    }
}
