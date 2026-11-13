package com.babegetthis.android.feature.gotthis.domain

import java.util.TimeZone

object ShopDay {

    // The weekday (1 = Monday) with the most purchase days in the last 56 days, or
    // null without enough days or a big enough share. A tie goes to the earlier weekday.
    fun usualWeekday(purchaseMillis: List<Long>, nowMillis: Long, tz: TimeZone): Int? {
        val today = Days.epochDay(nowMillis, tz)
        val recent = purchaseMillis.map { Days.epochDay(it, tz) }
            .filter { today - it in 0 until GotThisRules.SHOP_DAY_LOOKBACK_DAYS }
            .distinct()
        if (recent.size < GotThisRules.SHOP_DAY_MIN_DAYS) return null
        val perWeekday = recent.groupingBy { Days.weekday(it) }.eachCount()
        val top = perWeekday.entries.sortedWith(
            compareByDescending<Map.Entry<Int, Int>> {
                it.value
            }.thenBy { it.key },
        ).first()
        return top.key.takeIf { top.value >= GotThisRules.SHOP_DAY_MIN_SHARE * recent.size }
    }
}
