package com.babegetthis.android.feature.gotthis.domain

import java.util.TimeZone

// Calendar-day arithmetic on epoch days (days since 1970-01-01 in a time zone),
// because java.time is unavailable at minSdk 24.
object Days {

    const val DAY_MILLIS = 86_400_000L
    private const val HOUR_MILLIS = 3_600_000L

    fun epochDay(millis: Long, tz: TimeZone): Long = Math.floorDiv(millis + tz.getOffset(millis), DAY_MILLIS)

    // 1 = Monday ... 7 = Sunday. Epoch day 0 was a Thursday.
    fun weekday(epochDay: Long): Int = Math.floorMod(epochDay + 3, 7L).toInt() + 1

    // The ISO week-year is the year of the week's Thursday.
    fun isoWeek(millis: Long, tz: TimeZone): String {
        val day = epochDay(millis, tz)
        val thursday = day + (4 - weekday(day))
        val year = yearOf(thursday)
        val week = ((thursday - startOfYear(year)) / 7 + 1).toInt()
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    // The offset is looked up twice so a day with a clock change still lands on
    // the requested local hour.
    fun millisAtHour(epochDay: Long, hour: Int, tz: TimeZone): Long {
        val local = epochDay * DAY_MILLIS + hour * HOUR_MILLIS
        val first = local - tz.getOffset(local)
        return local - tz.getOffset(first)
    }

    // Civil-from-days (Howard Hinnant): the proleptic Gregorian year of an epoch day.
    private fun yearOf(epochDay: Long): Long {
        val z = epochDay + 719_468
        val era = Math.floorDiv(z, 146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val month = if (mp < 10) mp + 3 else mp - 9
        return yoe + era * 400 + if (month <= 2) 1 else 0
    }

    // Days-from-civil for January 1st of [year].
    private fun startOfYear(year: Long): Long {
        val y = year - 1 // January counts as month 13 of the previous year
        val era = Math.floorDiv(y, 400L)
        val yoe = y - era * 400
        val doy = (153 * (13 - 3) + 2) / 5 // day 1 of month 13 -> 306
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }
}
