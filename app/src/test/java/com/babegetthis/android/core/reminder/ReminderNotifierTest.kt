package com.babegetthis.android.core.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.MainActivity
import com.babegetthis.android.core.reminder.data.local.ReminderContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReminderNotifierTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var notifier: ReminderNotifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        notifier = ReminderNotifier(context, NotificationManagerCompat.from(context))
    }

    private fun content(
        names: List<String>,
        listId: String = "A",
        listName: String = "Weekly shop",
        isLocked: Boolean = false,
    ) = ReminderContent(
        listId = listId,
        listName = listName,
        isLocked = isLocked,
        pickedCount = 1,
        uncheckedNames = names,
    )

    @Test
    fun `one unticked item names it`() {
        assertEquals("Milk is still unticked", notifier.text(content(listOf("Milk"))).body)
    }

    @Test
    fun `two unticked items name both`() {
        assertEquals(
            "Milk and Eggs are still unticked",
            notifier.text(content(listOf("Milk", "Eggs"))).body,
        )
    }

    @Test
    fun `three or more name two and count the rest`() {
        assertEquals(
            "Milk, Eggs and 1 more are still unticked",
            notifier.text(content(listOf("Milk", "Eggs", "Bread"))).body,
        )
        assertEquals(
            "Milk, Eggs and 2 more are still unticked",
            notifier.text(content(listOf("Milk", "Eggs", "Bread", "Jam"))).body,
        )
    }

    @Test
    fun `a locked list withholds names`() {
        assertEquals(
            "3 items are still unticked",
            notifier.text(content(listOf("Milk", "Eggs", "Bread"), isLocked = true)).body,
        )
        assertEquals(
            "1 item is still unticked",
            notifier.text(content(listOf("Milk"), isLocked = true)).body,
        )
    }

    @Test
    fun `a long item name is cut to 40 characters with an ellipsis`() {
        val exactlyForty = "a".repeat(ReminderNotifier.MAX_NAME_CHARS)
        val fifty = "b".repeat(50)

        assertEquals(
            "$exactlyForty is still unticked",
            notifier.text(content(listOf(exactlyForty))).body,
        )

        val cut = notifier.text(content(listOf(fifty))).body
        assertEquals("${"b".repeat(39)}… is still unticked", cut)
        assertEquals(ReminderNotifier.MAX_NAME_CHARS, cut.substringBefore(" is still").length)
    }

    @Test
    fun `a long list name is cut the same way in the title`() {
        assertEquals(
            "Forget something on ${"c".repeat(39)}…?",
            notifier.text(content(listOf("Milk"), listName = "c".repeat(50))).title,
        )
    }

    @Test
    fun `the title carries the list name`() {
        assertEquals(
            "Forget something on Weekly shop?",
            notifier.text(content(listOf("Milk"))).title,
        )
    }

    @Test
    fun `posting twice leaves one notification carrying the second list`() {
        notifier.post(content(listOf("Milk"), listId = "A", listName = "A"))
        notifier.post(content(listOf("Eggs"), listId = "B", listName = "B"))

        val active = shadowOf(manager).activeNotifications
        assertEquals(1, active.size)
        assertEquals(ReminderNotifier.NOTIFICATION_ID, active[0].id)
        val notification = active[0].notification
        assertTrue(
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("B"),
        )
        assertEquals(
            "Eggs is still unticked",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals("B", intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_ID))
        assertEquals("B", intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_NAME))
    }

    @Test
    fun `the content intent has the required flags and extras`() {
        notifier.post(content(listOf("Milk"), listId = "list-1", listName = "Weekly shop"))

        val notification = shadowOf(manager).activeNotifications.single().notification
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)

        val shadow = shadowOf(notification.contentIntent)
        assertTrue(shadow.flags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
        assertTrue(shadow.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertEquals(ReminderNotifier.REQUEST_CODE, shadow.requestCode)

        val intent = shadow.savedIntent
        val required = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(required, intent.flags and required)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals("list-1", intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_ID))
        assertEquals("Weekly shop", intent.getStringExtra(ReminderIntentParser.EXTRA_LIST_NAME))
    }

    @Test
    fun `the notification uses the reminders channel and a small icon`() {
        notifier.post(content(listOf("Milk")))

        val notification = shadowOf(manager).activeNotifications.single().notification
        assertEquals(ReminderNotifier.CHANNEL_ID, notification.channelId)
        assertNotEquals(0, notification.smallIcon.resId)
    }

    @Test
    fun `the channel is created once across two posts`() {
        notifier.post(content(listOf("Milk")))
        notifier.post(content(listOf("Eggs")))

        val channels = shadowOf(manager).notificationChannels
            .filterIsInstance<NotificationChannel>()
            .filter { it.id == ReminderNotifier.CHANNEL_ID }
        assertEquals(1, channels.size)
        assertEquals("Shopping reminders", channels.single().name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channels.single().importance)
    }
}
