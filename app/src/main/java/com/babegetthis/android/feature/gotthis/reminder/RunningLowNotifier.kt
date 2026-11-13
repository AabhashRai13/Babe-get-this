package com.babegetthis.android.feature.gotthis.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.babegetthis.android.MainActivity
import com.babegetthis.android.R
import com.babegetthis.android.core.reminder.ReminderIntentParser
import com.babegetthis.android.core.reminder.ReminderNotifier
import com.babegetthis.android.core.reminder.ReminderTarget

/**
 * Posts the weekly "Running low?" notification. Like [ReminderNotifier] it holds
 * no decisions: the worker has settled whether to post before calling [post].
 */
class RunningLowNotifier(private val context: Context, private val notifications: NotificationManagerCompat) {

    /** Title and body for up to three [names] and [more] further items. */
    fun text(names: List<String>, more: Int): Pair<String, String> {
        val res = context.resources
        val clipped = names.map(ReminderNotifier.Companion::clip)
        val body = when {
            more > 0 -> res.getString(
                R.string.got_this_notification_more,
                clipped[0],
                clipped[1],
                clipped[2],
                more,
            )
            clipped.size == 1 -> res.getString(R.string.got_this_notification_one, clipped[0])
            clipped.size == 2 -> res.getString(R.string.got_this_notification_two, clipped[0], clipped[1])
            else -> res.getString(R.string.got_this_notification_three, clipped[0], clipped[1], clipped[2])
        }
        return res.getString(R.string.got_this_notification_title) to body
    }

    /**
     * False when the person turned this channel off in the system settings: a
     * post would be dropped silently. A channel not created yet (nothing posted)
     * is on, and before Android 8 there are no channels.
     */
    fun channelEnabled(): Boolean =
        notifications.getNotificationChannelCompat(CHANNEL_ID)?.importance != NotificationManagerCompat.IMPORTANCE_NONE

    // POST_NOTIFICATIONS is checked by the caller: RunningLowWorker only posts
    // after areNotificationsEnabled() said yes.
    @SuppressLint("MissingPermission")
    fun post(names: List<String>, more: Int, target: ReminderTarget?) {
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.got_this_channel_name))
                .build(),
        )
        val (title, body) = text(names, more)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(ReminderIntentParser.EXTRA_SOURCE, ReminderIntentParser.SOURCE_GOT_THIS)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        if (target != null) {
            intent.putExtra(ReminderIntentParser.EXTRA_LIST_ID, target.listId)
                .putExtra(ReminderIntentParser.EXTRA_LIST_NAME, target.listName)
        } else {
            intent.putExtra(ReminderIntentParser.EXTRA_CREATE_LIST, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "got_this_running_low"
        const val NOTIFICATION_ID = 1002
        const val REQUEST_CODE = 1002
    }
}
