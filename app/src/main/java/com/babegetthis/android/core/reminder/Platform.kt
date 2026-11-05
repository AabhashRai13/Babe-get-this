package com.babegetthis.android.core.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

// Platform seams the scheduler and worker read, so both stay testable off-device.

/** Whether the OS currently lets this app post notifications. */
fun interface NotificationGate {
    fun notificationsEnabled(): Boolean
}

/** Whether the app process is in the foreground. */
fun interface ForegroundState {
    fun isForeground(): Boolean
}

/** Wall-clock milliseconds. */
fun interface ReminderClock {
    fun now(): Long
}

/** Reads the OS-level notification setting (app-wide toggle and, on 13+, the permission). */
class AndroidNotificationGate(private val context: Context) : NotificationGate {
    override fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * True while any activity of the process is at least STARTED. Called from a
 * worker thread: `LifecycleRegistry.currentState` is a plain read and is not
 * main-thread-enforced, so no hop to the main looper is needed.
 */
class ProcessForegroundState : ForegroundState {
    override fun isForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}

object SystemReminderClock : ReminderClock {
    override fun now(): Long = System.currentTimeMillis()
}
