package com.babegetthis.android.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

// Every case pins "now" explicitly. Before getTimePeriod took a nowMillis
// parameter these assertions read the wall clock, so the 7-day and same-month
// boundaries meant something different depending on the date the suite ran —
// "20 days ago" is this month on the 25th and last month on the 5th.
class DateGroupingTest {

    // 2024-06-25 12:00:00 local. Mid-month and midday on purpose: leaves room
    // either side for the same-month cases without crossing a month boundary,
    // and keeps day arithmetic away from midnight/DST edges.
    private fun now(): Calendar = Calendar.getInstance().apply {
        set(2024, Calendar.JUNE, 25, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private val nowMillis = now().timeInMillis

    // Built by cloning `now` and calling add(), the same way production does, so
    // the test isn't asserting against hand-rolled millisecond arithmetic.
    private fun daysBefore(days: Int, hoursOffset: Int = 0): Long =
        now().apply {
            add(Calendar.DAY_OF_YEAR, -days)
            add(Calendar.HOUR_OF_DAY, hoursOffset)
        }.timeInMillis

    private fun period(timestamp: Long) = getTimePeriod(timestamp, nowMillis)

    @Test
    fun `same calendar day is TODAY`() {
        assertEquals(TimePeriod.TODAY, period(daysBefore(0, hoursOffset = -5)))
    }

    @Test
    fun `start of today is still TODAY`() {
        val startOfToday = now().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        assertEquals(TimePeriod.TODAY, period(startOfToday))
    }

    @Test
    fun `previous calendar day is YESTERDAY`() {
        assertEquals(TimePeriod.YESTERDAY, period(daysBefore(1)))
    }

    @Test
    fun `two days ago is LAST_WEEK`() {
        assertEquals(TimePeriod.LAST_WEEK, period(daysBefore(2)))
    }

    @Test
    fun `six days ago is LAST_WEEK`() {
        assertEquals(TimePeriod.LAST_WEEK, period(daysBefore(6)))
    }

    // The boundary is exclusive: the check is `date.after(sevenDaysAgo)`, so a
    // timestamp landing exactly on the cutoff falls through to the month check.
    @Test
    fun `exactly seven days ago falls through to THIS_MONTH`() {
        assertEquals(TimePeriod.THIS_MONTH, period(daysBefore(7)))
    }

    @Test
    fun `just inside seven days is LAST_WEEK`() {
        assertEquals(TimePeriod.LAST_WEEK, period(daysBefore(7) + 1))
    }

    @Test
    fun `earlier in the same month is THIS_MONTH`() {
        // 2024-06-05, still June.
        assertEquals(TimePeriod.THIS_MONTH, period(daysBefore(20)))
    }

    @Test
    fun `previous month is OLDER`() {
        // 2024-05-26.
        assertEquals(TimePeriod.OLDER, period(daysBefore(30)))
    }

    @Test
    fun `same month in a previous year is OLDER`() {
        val lastYear = now().apply { add(Calendar.YEAR, -1) }.timeInMillis
        assertEquals(TimePeriod.OLDER, period(lastYear))
    }

    // Pinning current behavior rather than endorsing it: a future timestamp is
    // after `sevenDaysAgo`, so it reads as LAST_WEEK. Reachable only via a device
    // clock set backwards or a bad import — harmless, but it should change
    // deliberately rather than by accident.
    @Test
    fun `future timestamp reads as LAST_WEEK`() {
        val tomorrow = now().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        assertEquals(TimePeriod.LAST_WEEK, period(tomorrow))
    }

    @Test
    fun `displayName covers every period`() {
        assertEquals("Today", TimePeriod.TODAY.displayName())
        assertEquals("Yesterday", TimePeriod.YESTERDAY.displayName())
        assertEquals("Last 7 Days", TimePeriod.LAST_WEEK.displayName())
        assertEquals("This Month", TimePeriod.THIS_MONTH.displayName())
        assertEquals("Older", TimePeriod.OLDER.displayName())
    }

    @Test
    fun `displayName is exhaustive over the enum`() {
        // Guards the `when` above staying total if a period is ever added.
        assertEquals(5, TimePeriod.entries.size)
        TimePeriod.entries.forEach { assertTrue(it.displayName().isNotBlank()) }
    }

    private fun assertTrue(value: Boolean) = assertEquals(true, value)
}
