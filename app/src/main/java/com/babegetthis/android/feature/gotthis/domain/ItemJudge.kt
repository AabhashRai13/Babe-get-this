package com.babegetthis.android.feature.gotthis.domain

import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.floor

enum class HintSource { Regular, Category }

data class JudgedPurchase(val boughtAt: Long, val categoryId: String?)

data class ItemProfile(
    val shopDays: List<Long>,
    val usualGapDays: Double?,
    val isRegular: Boolean,
    val isLapsed: Boolean,
    val daysSinceLastShop: Int?,
    val isDue: Boolean,
    val daysOverdue: Int,
    val hintWindowDays: Int,
    val hintSource: HintSource,
    val showsHint: Boolean,
    val hintDaysAgo: Int?,
)

// Turns one item's purchase history into what the app shows about it.
object ItemJudge {

    fun profile(purchases: List<JudgedPurchase>, nowMillis: Long, tz: TimeZone): ItemProfile {
        val today = Days.epochDay(nowMillis, tz)
        val past = purchases.filter { Days.epochDay(it.boughtAt, tz) <= today }.sortedBy { it.boughtAt }
        val shopDays = window(shopDays(past.map { Days.epochDay(it.boughtAt, tz) }))
        val m = usualGap(shopDays)
        val sinceShop = shopDays.lastOrNull()?.let { (today - it).toInt() }
        val holds = m != null && meetsRules(shopDays, m)
        val isLapsed = holds && sinceShop!! > GotThisRules.LAPSE_FACTOR * m!!
        val isRegular = holds && !isLapsed
        val dueAfter = if (isRegular) ceil(GotThisRules.DUE_FACTOR * m!!).toInt() else 0
        val isDue = isRegular && sinceShop!! >= dueAfter
        val hintWindow = if (isRegular) {
            maxOf(1, floor(m!! / 2).toInt())
        } else {
            GotThisRules.categoryWindowDays(past.lastOrNull { it.categoryId != null }?.categoryId)
        }
        val hintDaysAgo = past.lastOrNull()?.let { (today - Days.epochDay(it.boughtAt, tz)).toInt() }
        return ItemProfile(
            shopDays = shopDays,
            usualGapDays = m,
            isRegular = isRegular,
            isLapsed = isLapsed,
            daysSinceLastShop = sinceShop,
            isDue = isDue,
            daysOverdue = if (isDue) sinceShop!! - dueAfter else 0,
            hintWindowDays = hintWindow,
            hintSource = if (isRegular) HintSource.Regular else HintSource.Category,
            showsHint = hintDaysAgo != null && hintDaysAgo < hintWindow,
            hintDaysAgo = hintDaysAgo,
        )
    }

    // Epoch day of each shop's first day, from ascending purchase days.
    internal fun shopDays(purchaseDays: List<Long>): List<Long> {
        val shops = mutableListOf<Long>()
        purchaseDays.forEach { day ->
            if (shops.isEmpty() || day - shops.last() >= GotThisRules.SHOP_JOIN_DAYS) shops += day
        }
        return shops
    }

    internal fun window(shops: List<Long>): List<Long> = shops.takeLast(GotThisRules.WINDOW_SHOPS)

    // Median gap of a window; null with fewer than two shops.
    internal fun usualGap(window: List<Long>): Double? {
        if (window.size < 2) return null
        val gaps = window.zipWithNext { a, b -> b - a }.sorted()
        val mid = gaps.size / 2
        return if (gaps.size % 2 == 1) gaps[mid].toDouble() else (gaps[mid - 1] + gaps[mid]) / 2.0
    }

    // The three rules: enough shops, enough span, enough steady gaps.
    internal fun meetsRules(window: List<Long>, m: Double): Boolean {
        if (window.size < GotThisRules.MIN_SHOPS) return false
        if (window.last() - window.first() < GotThisRules.MIN_SPAN_DAYS) return false
        val gaps = window.zipWithNext { a, b -> b - a }
        val steady = gaps.count {
            it >= GotThisRules.STEADY_LOW_FACTOR * m && it <= GotThisRules.STEADY_HIGH_FACTOR * m
        }
        return steady >= ceil(GotThisRules.STEADY_SHARE * gaps.size).toInt()
    }
}

// The forecast held after the previous shop, scored when the newest purchase
// starts a new shop: positive means bought later than predicted. Null unless the
// item was regular (lapse ignored) on the history before that purchase.
fun ItemJudge.scoreLatestShop(purchases: List<JudgedPurchase>, tz: TimeZone): Int? {
    val days = purchases.sortedBy { it.boughtAt }.map { Days.epochDay(it.boughtAt, tz) }
    val newest = days.lastOrNull() ?: return null
    val window = window(shopDays(days.dropLast(1)))
    val m = usualGap(window)
    val startsNewShop = window.isNotEmpty() && newest - window.last() >= GotThisRules.SHOP_JOIN_DAYS
    if (m == null || !startsNewShop || !meetsRules(window, m)) return null
    return (newest - (window.last() + floor(m + 0.5).toLong())).toInt()
}
