package com.babegetthis.android.feature.gotthis.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.MainActivity
import com.babegetthis.android.core.reminder.ReminderIntentParser
import com.babegetthis.android.core.reminder.ReminderTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RunningLowNotifierTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var notifier: RunningLowNotifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        notifier = RunningLowNotifier(context, NotificationManagerCompat.from(context))
    }

    @Test
    fun `one name`() {
        val (title, body) = notifier.text(listOf("Eggs"), 0)

        assertEquals("Running low?", title)
        assertEquals("Eggs is usually bought around now.", body)
    }

    @Test
    fun `two names`() {
        assertEquals(
            "Eggs and milk are usually bought around now.",
            notifier.text(listOf("Eggs", "milk"), 0).second,
        )
    }

    @Test
    fun `three names`() {
        assertEquals(
            "Eggs, milk and bread are usually bought around now.",
            notifier.text(listOf("Eggs", "milk", "bread"), 0).second,
        )
    }

    @Test
    fun `more than three counts the rest`() {
        assertEquals(
            "Eggs, milk, bread and 2 more are usually bought around now.",
            notifier.text(listOf("Eggs", "milk", "bread"), 2).second,
        )
    }

    @Test
    fun `a long name is cut to 40 characters with an ellipsis`() {
        val body = notifier.text(listOf("x".repeat(60)), 0).second

        assertEquals("${"x".repeat(39)}… is usually bought around now.", body)
    }

    @Test
    fun `with a list the intent carries the list and the source`() {
        notifier.post(listOf("Eggs"), 0, ReminderTarget("list-1", "Weekly shop"))

        val active = shadowOf(manager).activeNotifications.single()
        assertEquals(RunningLowNotifier.NOTIFICATION_ID, active.id)
        assertEquals(1002, active.id)
        val notification = active.notification
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertNotEquals(0, notification.smallIcon.resId)
        assertEquals(
            "Eggs is usually bought around now.",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        val shadow = shadowOf(notification.contentIntent)
        assertEquals(1002, shadow.requestCode)
        assertTrue(shadow.flags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
        assertTrue(shadow.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        val intent = shadow.savedIntent
        val required = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(required, intent.flags and required)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals("list-1", intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_ID))
        assertEquals("Weekly shop", intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_NAME))
        assertEquals("got_this", intent.getStringExtra(ReminderIntentParser.EXTRA_SOURCE))
        assertFalse(intent.hasExtra(ReminderIntentParser.EXTRA_CREATE_LIST))
    }

    @Test
    fun `without a list the intent asks to create one`() {
        notifier.post(listOf("Eggs"), 0, null)

        val intent = shadowOf(shadowOf(manager).activeNotifications.single().notification.contentIntent).savedIntent
        assertNull(intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_ID))
        assertNull(intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_NAME))
        assertEquals("got_this", intent.getStringExtra(ReminderIntentParser.EXTRA_SOURCE))
        assertTrue(intent.getBooleanExtra(ReminderIntentParser.EXTRA_CREATE_LIST, false))
    }

    @Test
    fun `the channel is on until the person turns it off`() {
        assertTrue(notifier.channelEnabled())
        notifier.post(listOf("Eggs"), 0, null)
        assertTrue(notifier.channelEnabled())

        manager.deleteNotificationChannel(RunningLowNotifier.CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(RunningLowNotifier.CHANNEL_ID, "Running low", NotificationManager.IMPORTANCE_NONE),
        )

        assertFalse(notifier.channelEnabled())
    }

    @Test
    fun `it has its own channel, created once`() {
        notifier.post(listOf("Eggs"), 0, null)
        notifier.post(listOf("Milk"), 0, null)

        val channels = shadowOf(manager).notificationChannels
            .filterIsInstance<NotificationChannel>()
            .filter { it.id == "got_this_running_low" }
        assertEquals(1, channels.size)
        assertEquals("Running-low reminders", channels.single().name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channels.single().importance)
        assertEquals(1, shadowOf(manager).activeNotifications.size)
    }
}
