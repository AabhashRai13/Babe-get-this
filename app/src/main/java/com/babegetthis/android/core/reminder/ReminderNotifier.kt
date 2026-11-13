package com.babegetthis.android.core.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.babegetthis.android.MainActivity
import com.babegetthis.android.R
import com.babegetthis.android.core.reminder.data.local.ReminderContent

data class ReminderText(val title: String, val body: String)

/**
 * Turns a [ReminderContent] into a posted notification. It holds no decisions
 * and reads no data: whether to remind is settled before [post] is called.
 */
class ReminderNotifier(private val context: Context, private val notifications: NotificationManagerCompat) {

    fun text(content: ReminderContent): ReminderText {
        val res = context.resources
        val names = content.uncheckedNames.map { clip(it) }
        val body = when {
            content.isLocked -> res.getQuantityString(
                R.plurals.reminder_body_locked,
                content.uncheckedCount,
                content.uncheckedCount,
            )
            content.uncheckedCount == 1 -> res.getString(R.string.reminder_body_one, names[0])
            content.uncheckedCount == 2 -> res.getString(R.string.reminder_body_two, names[0], names[1])
            else -> res.getString(
                R.string.reminder_body_more,
                names[0],
                names[1],
                content.uncheckedCount - 2,
            )
        }
        return ReminderText(
            title = res.getString(R.string.reminder_title, clip(content.listName)),
            body = body,
        )
    }

    // POST_NOTIFICATIONS is checked by the caller: ReminderWorker re-checks
    // areNotificationsEnabled() at fire time (false on 13+ without the grant)
    // and only then calls post, so notify() is never reached without it.
    @SuppressLint("MissingPermission")
    fun post(content: ReminderContent) {
        // Creating a channel that already exists is a no-op by platform contract,
        // so this needs no "have I done this" bookkeeping.
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.reminder_channel_name))
                .build(),
        )
        val text = text(content)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(ReminderIntentParser.EXTRA_LIST_ID, content.listId)
            .putExtra(ReminderIntentParser.EXTRA_LIST_NAME, content.listName)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }

    /** Removes a posted reminder, so sign-out, toggle-off and flag-off leave nothing on screen. */
    fun cancel() {
        notifications.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        const val NOTIFICATION_ID = 1001
        const val REQUEST_CODE = 1001
        const val MAX_NAME_CHARS = 40
        private const val ELLIPSIS = "…"

        // Counted in code points, not UTF-16 chars, so an emoji at the cut is kept
        // or dropped whole rather than split into a lone surrogate.
        fun clip(name: String): String = if (name.codePointCount(0, name.length) > MAX_NAME_CHARS) {
            name.substring(0, name.offsetByCodePoints(0, MAX_NAME_CHARS - 1)) + ELLIPSIS
        } else {
            name
        }
    }
}
