package com.babegetthis.android.core.reminder

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
