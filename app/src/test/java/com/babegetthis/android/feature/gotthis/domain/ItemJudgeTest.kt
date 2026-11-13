package com.babegetthis.android.feature.gotthis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ItemJudgeTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val london = TimeZone.getTimeZone("Europe/London")
    private val baseDay = 20_000L
    private val hour = 3_600_000L
    private val dayMillis = 86_400_000L

    private fun noon(offset: Long): Long = (baseDay + offset) * dayMillis + 12 * hour

    private fun days(vararg offsets: Long, category: String? = null): List<JudgedPurchase> =
        offsets.map { JudgedPurchase(noon(it), category) }

    private fun judge(purchases: List<JudgedPurchase>, today: Long) = ItemJudge.profile(purchases, noon(today), utc)

    private fun weekly(shops: Int): LongArray = LongArray(shops) { it * 7L }

    @Test
    fun `one purchase is not regular and hints by category`() {
        val p = judge(days(0, category = "cat-dairy-eggs"), 3)
        assertFalse(p.isRegular)
        assertNull(p.usualGapDays)
        assertEquals(HintSource.Category, p.hintSource)
        assertEquals(5, p.hintWindowDays)
        assertTrue(p.showsHint)
        assertEquals(3, p.hintDaysAgo)
        assertEquals(listOf(baseDay), p.shopDays)
    }

    @Test
    fun `three weekly shops are regular`() {
        val p = judge(days(0, 7, 14), 14)
        assertTrue(p.isRegular)
        assertFalse(p.isLapsed)
        assertEquals(7.0, p.usualGapDays!!, 0.0)
        assertEquals(HintSource.Regular, p.hintSource)
        assertEquals(3, p.hintWindowDays)
        assertTrue(p.showsHint)
        assertEquals(0, p.hintDaysAgo)
    }

    @Test
    fun `two shops are not regular but have a usual gap`() {
        val p = judge(days(0, 7), 7)
        assertFalse(p.isRegular)
        assertEquals(7.0, p.usualGapDays!!, 0.0)
    }

    @Test
    fun `a party week fails the span rule`() {
        assertFalse(judge(days(0, 1, 2), 2).isRegular)
        assertFalse(judge(days(0, 2, 4), 4).isRegular)
    }

    @Test
    fun `consecutive days are one shop and two days apart are two`() {
        assertEquals(1, judge(days(0, 1), 1).shopDays.size)
        assertEquals(2, judge(days(0, 2), 2).shopDays.size)
    }

    @Test
    fun `purchases on the same day are one shop`() {
        assertEquals(1, judge(days(0, 0), 0).shopDays.size)
    }

    @Test
    fun `one extra purchase does not break a weekly item`() {
        val p = judge(days(0, 7, 9, 14, 21, 28, 35), 35)
        assertTrue(p.isRegular)
        assertEquals(7.0, p.usualGapDays!!, 0.0)
    }

    @Test
    fun `a long break leaves the item regular on the day it is bought again`() {
        val p = judge(days(0, 7, 14, 21, 28, 35, 95), 95)
        assertTrue(p.isRegular)
        assertEquals(7.0, p.usualGapDays!!, 0.0)
    }

    @Test
    fun `erratic gaps fail the steady rule`() {
        val p = judge(days(0, 3, 33, 37, 77), 77)
        assertFalse(p.isRegular)
        assertFalse(p.isLapsed)
    }

    @Test
    fun `only the last seven shops count`() {
        assertEquals(7, judge(days(*weekly(9)), 56).shopDays.size)
    }

    @Test
    fun `rules are computed inside the window only`() {
        // an old odd gap has left the window
        val p = judge(days(0, 3, 33, 40, 47, 54, 61, 68, 75), 75)
        assertTrue(p.isRegular)
        assertEquals(7.0, p.usualGapDays!!, 0.0)
    }

    @Test
    fun `monthly shops get a half month window`() {
        val p = judge(days(0, 30, 60), 60)
        assertTrue(p.isRegular)
        assertEquals(15, p.hintWindowDays)
    }

    @Test
    fun `an even number of gaps uses the mean of the middle two`() {
        val p = judge(days(0, 7, 15), 15)
        assertEquals(7.5, p.usualGapDays!!, 0.0)
        assertEquals(3, p.hintWindowDays)
        assertFalse(judge(days(0, 7, 15), 21).isDue)
        assertTrue(judge(days(0, 7, 15), 22).isDue)
    }

    @Test
    fun `a tiny gap still gets a window of one day`() {
        // gaps of 3 days: floor(1.5) = 1
        val p = judge(days(0, 3, 6, 9, 12, 15, 18), 18)
        assertTrue(p.isRegular)
        assertEquals(1, p.hintWindowDays)
        assertTrue(p.showsHint)
        assertFalse(judge(days(0, 3, 6, 9, 12, 15, 18), 19).showsHint)
    }

    @Test
    fun `due and overdue follow the days since the last shop`() {
        val weekly = days(0, 7, 14)
        val six = judge(weekly, 20)
        assertFalse(six.isDue)
        assertEquals(0, six.daysOverdue)
        val seven = judge(weekly, 21)
        assertTrue(seven.isDue)
        assertEquals(0, seven.daysOverdue)
        assertEquals(2, judge(weekly, 23).daysOverdue)
    }

    @Test
    fun `an item unbought for more than two and a half gaps is lapsed`() {
        val weekly = days(0, 7, 14)
        val edge = judge(weekly, 31) // 17 days, not over 17.5
        assertTrue(edge.isRegular)
        assertFalse(edge.isLapsed)
        val lapsed = judge(weekly, 32) // 18 days
        assertTrue(lapsed.isLapsed)
        assertFalse(lapsed.isRegular)
        assertFalse(lapsed.isDue)
        assertEquals(0, lapsed.daysOverdue)
        assertEquals(HintSource.Category, lapsed.hintSource)
        assertEquals(7, lapsed.hintWindowDays)
    }

    @Test
    fun `a fresh item bought a week ago gets no hint`() {
        val p = judge(days(0, category = "cat-meat-seafood"), 7)
        assertFalse(p.showsHint)
        assertEquals(7, p.hintDaysAgo)
    }

    @Test
    fun `a long-life item bought twelve days ago gets a hint`() {
        val p = judge(days(0, category = "cat-household-cleaning"), 12)
        assertTrue(p.showsHint)
    }

    @Test
    fun `the latest purchase with a category decides the window`() {
        val purchases = listOf(
            JudgedPurchase(noon(0), "cat-clothing"),
            JudgedPurchase(noon(5), null),
        )
        val p = judge(purchases, 10)
        assertEquals(90, p.hintWindowDays)
        assertEquals(5, p.hintDaysAgo)
    }

    @Test
    fun `no category at all means the unknown window`() {
        assertEquals(7, judge(days(0), 1).hintWindowDays)
    }

    @Test
    fun `hint age counts from the latest purchase not the shop`() {
        // days 0 and 1 are one shop; the hint counts from day 1
        val p = judge(days(0, 1, category = "cat-snacks"), 3)
        assertEquals(3, p.daysSinceLastShop)
        assertEquals(2, p.hintDaysAgo)
    }

    @Test
    fun `purchases dated tomorrow are ignored`() {
        val p = judge(days(0, 1), 0)
        assertEquals(1, p.shopDays.size)
        assertEquals(0, p.hintDaysAgo)
    }

    @Test
    fun `unsorted purchases are judged in time order`() {
        val p = judge(days(14, 0, 7), 14)
        assertEquals(listOf(baseDay, baseDay + 7, baseDay + 14), p.shopDays)
    }

    @Test
    fun `late evening and just after midnight are one shop of two local days`() {
        val late = Calendar.getInstance(london).apply {
            clear()
            set(2026, Calendar.JUNE, 10, 23, 30, 0)
        }.timeInMillis
        val early = late + hour
        val now = early + hour
        val p = ItemJudge.profile(
            listOf(JudgedPurchase(late, null), JudgedPurchase(early, null)),
            now,
            london,
        )
        assertEquals(1, p.shopDays.size)
        assertEquals(0, p.hintDaysAgo)
        assertEquals(Days.epochDay(late, london), p.shopDays.single())
        assertEquals(Days.epochDay(late, london) + 1, Days.epochDay(early, london))
    }

    @Test
    fun `no purchases means nothing is true`() {
        val p = judge(emptyList(), 0)
        assertTrue(p.shopDays.isEmpty())
        assertNull(p.usualGapDays)
        assertFalse(p.isRegular)
        assertFalse(p.isLapsed)
        assertNull(p.daysSinceLastShop)
        assertFalse(p.isDue)
        assertEquals(0, p.daysOverdue)
        assertEquals(7, p.hintWindowDays)
        assertEquals(HintSource.Category, p.hintSource)
        assertFalse(p.showsHint)
        assertNull(p.hintDaysAgo)
    }
}
