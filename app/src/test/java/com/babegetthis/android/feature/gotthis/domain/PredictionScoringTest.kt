package com.babegetthis.android.feature.gotthis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class PredictionScoringTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val baseDay = 20_000L

    private fun days(vararg offsets: Long): List<JudgedPurchase> =
        offsets.map { JudgedPurchase((baseDay + it) * 86_400_000L + 12 * 3_600_000L, null) }

    private fun score(vararg offsets: Long): Int? = ItemJudge.scoreLatestShop(days(*offsets), utc)

    @Test
    fun `a shop on the predicted day scores zero`() {
        assertEquals(0, score(0, 7, 14, 21))
    }

    @Test
    fun `a late shop scores positive`() {
        assertEquals(2, score(0, 7, 14, 23))
    }

    @Test
    fun `an early shop scores negative`() {
        assertEquals(-2, score(0, 7, 14, 19))
    }

    @Test
    fun `a lapsed item is still scored`() {
        assertEquals(53, score(0, 7, 14, 74))
    }

    @Test
    fun `fewer than three earlier shops is not scored`() {
        assertNull(score(0, 7, 14))
    }

    @Test
    fun `a purchase joining the last shop is not scored`() {
        assertNull(score(0, 7, 14, 15))
    }

    @Test
    fun `no purchases is not scored`() {
        assertNull(score())
    }

    @Test
    fun `an irregular history is not scored`() {
        assertNull(score(0, 3, 33, 37, 77, 90))
    }

    @Test
    fun `the predicted day rounds half up`() {
        // gaps 7 and 8 give m = 7.5, predicted day 15 + 8
        assertEquals(1, score(0, 7, 15, 24))
        assertEquals(0, score(0, 7, 15, 23))
    }

    @Test
    fun `purchases are taken in time order`() {
        assertEquals(0, score(21, 0, 14, 7))
    }
}
