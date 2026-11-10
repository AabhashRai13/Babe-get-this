package com.babegetthis.android.feature.gotthis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class ShopDayTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val dayMillis = 86_400_000L

    // Epoch day 20_001 is a Saturday (20_001 + 3 = 20_004 = 7 * 2857 + 5).
    private val today = 20_001L
    private val now = today * dayMillis + 12 * 3_600_000L

    private fun ago(vararg daysAgo: Long): List<Long> = daysAgo.map { now - it * dayMillis }

    private fun usual(millis: List<Long>): Int? = ShopDay.usualWeekday(millis, now, utc)

    @Test
    fun `most common weekday wins`() {
        // four Saturdays (0, 7, 14, 21 days ago) and two Wednesdays (3, 10)
        assertEquals(6, usual(ago(0, 7, 14, 21, 3, 10)))
    }

    @Test
    fun `scattered weekdays do not count`() {
        assertNull(usual(ago(0, 1, 2, 3, 4)))
    }

    @Test
    fun `fewer than four purchase days do not count`() {
        assertNull(usual(ago(0, 7, 14)))
    }

    @Test
    fun `purchases older than 56 days are ignored`() {
        assertNull(usual(ago(56, 63, 70, 77, 84, 0)))
        assertEquals(6, usual(ago(0, 7, 14, 21, 56, 63)))
    }

    @Test
    fun `the oldest counted day is 55 days ago`() {
        assertEquals(7, usual(ago(55, 41, 27, 13)))
        assertNull(usual(ago(56, 42, 28, 14)))
    }

    @Test
    fun `two purchases on one day count once`() {
        assertNull(usual(ago(0, 0, 7, 14)))
        assertEquals(6, usual(ago(0, 0, 7, 14, 21)))
    }

    @Test
    fun `a tie goes to the earlier weekday`() {
        // two Saturdays (0, 7 days ago) and two Sundays (6, 13 days ago)
        assertEquals(6, usual(ago(0, 7, 6, 13)))
    }
}
