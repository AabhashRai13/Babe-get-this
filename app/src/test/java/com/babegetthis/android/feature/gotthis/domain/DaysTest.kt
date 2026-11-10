package com.babegetthis.android.feature.gotthis.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DaysTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val london = TimeZone.getTimeZone("Europe/London")

    private fun utcMillis(y: Int, month: Int, d: Int, h: Int, min: Int = 0): Long = Calendar.getInstance(utc).apply {
        clear()
        set(y, month - 1, d, h, min, 0)
    }.timeInMillis

    private fun hourIn(millis: Long, tz: TimeZone): Int =
        Calendar.getInstance(tz).apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    @Test
    fun `pinned instant is a Sunday`() {
        assertEquals(7, Days.weekday(Days.epochDay(1793527200000L, utc)))
    }

    @Test
    fun `epoch day zero is a Thursday`() {
        assertEquals(4, Days.weekday(0))
    }

    @Test
    fun `monday is week 46 of 2026`() {
        val m = utcMillis(2026, 11, 9, 12)
        assertEquals("2026-W46", Days.isoWeek(m, utc))
        assertEquals(1, Days.weekday(Days.epochDay(m, utc)))
    }

    @Test
    fun `new year day 2027 belongs to week 53 of 2026`() {
        assertEquals("2026-W53", Days.isoWeek(utcMillis(2027, 1, 1, 12), utc))
    }

    @Test
    fun `end of december 2024 belongs to week 1 of 2025`() {
        assertEquals("2025-W01", Days.isoWeek(utcMillis(2024, 12, 30, 12), utc))
    }

    @Test
    fun `week number is zero padded`() {
        assertEquals("2026-W01", Days.isoWeek(utcMillis(2026, 1, 1, 12), utc))
    }

    @Test
    fun `days either side of the london spring change are consecutive`() {
        val before = Days.epochDay(utcMillis(2026, 3, 28, 23, 30), london)
        val after = Days.epochDay(utcMillis(2026, 3, 29, 0, 30), london)
        assertEquals(before + 1, after)
    }

    @Test
    fun `millisAtHour lands on the requested hour across the london change`() {
        listOf(utcMillis(2026, 3, 28, 12), utcMillis(2026, 3, 29, 12), utcMillis(2026, 3, 30, 12)).forEach { x ->
            val day = Days.epochDay(x, london)
            val at = Days.millisAtHour(day, 18, london)
            assertEquals(18, hourIn(at, london))
            assertEquals(day, Days.epochDay(at, london))
        }
    }

    @Test
    fun `millisAtHour lands on the requested hour across the autumn change`() {
        val day = Days.epochDay(utcMillis(2026, 10, 25, 12), london)
        assertEquals(18, hourIn(Days.millisAtHour(day, 18, london), london))
    }

    @Test
    fun `negative millis floor to the previous day`() {
        assertEquals(-1L, Days.epochDay(-1L, utc))
        assertEquals(3, Days.weekday(Days.epochDay(-1L, utc)))
    }

    @Test
    fun `weekday of negative epoch days wraps`() {
        assertEquals(7, Days.weekday(-4))
    }
}
