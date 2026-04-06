package com.babegetthis.android.core.util

import java.util.Calendar

// Groups a timestamp into a human-readable time period.
// Like how WhatsApp or iMessage groups messages by date.

enum class TimePeriod {
    TODAY,
    YESTERDAY,
    LAST_WEEK,
    THIS_MONTH,
    OLDER,
}

fun getTimePeriod(timestampMillis: Long): TimePeriod {
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = timestampMillis }

    // Same day = Today
    if (isSameDay(now, date)) return TimePeriod.TODAY

    // Previous day = Yesterday
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (isSameDay(yesterday, date)) return TimePeriod.YESTERDAY

    // Within 7 days = Last Week
    val sevenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
    if (date.after(sevenDaysAgo)) return TimePeriod.LAST_WEEK

    // Same month and year = This Month
    if (now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
        now.get(Calendar.MONTH) == date.get(Calendar.MONTH)
    ) return TimePeriod.THIS_MONTH

    return TimePeriod.OLDER
}

fun TimePeriod.displayName(): String = when (this) {
    TimePeriod.TODAY -> "Today"
    TimePeriod.YESTERDAY -> "Yesterday"
    TimePeriod.LAST_WEEK -> "Last 7 Days"
    TimePeriod.THIS_MONTH -> "This Month"
    TimePeriod.OLDER -> "Older"
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
