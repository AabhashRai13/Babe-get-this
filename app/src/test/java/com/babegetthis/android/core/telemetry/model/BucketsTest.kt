package com.babegetthis.android.core.telemetry.model

import org.junit.Assert.assertEquals
import org.junit.Test

// Bucket boundaries are the difference between a readable report and a
// dimension with one value per user, so they are worth pinning exactly —
// including the off-by-one edges, which is where a `<` that should be a `<=`
// hides.
class BucketsTest {

    @Test
    fun `count buckets land on the documented edges`() {
        assertEquals("0", bucketCount(0))
        assertEquals("1", bucketCount(1))
        assertEquals("2-5", bucketCount(2))
        assertEquals("2-5", bucketCount(5))
        assertEquals("6-10", bucketCount(6))
        assertEquals("6-10", bucketCount(10))
        assertEquals("11-20", bucketCount(11))
        assertEquals("11-20", bucketCount(20))
        assertEquals("21-50", bucketCount(21))
        assertEquals("21-50", bucketCount(50))
        assertEquals("50+", bucketCount(51))
    }

    @Test
    fun `negative counts collapse into zero rather than producing a new label`() {
        // Nothing should hand this a negative, but a stray subtraction that
        // does must not invent an unbounded dimension value.
        assertEquals("0", bucketCount(-1))
        assertEquals("0", bucketCount(Int.MIN_VALUE))
    }

    @Test
    fun `large counts stay inside the top bucket`() {
        assertEquals("50+", bucketCount(4_318))
        assertEquals("50+", bucketCount(Int.MAX_VALUE))
    }

    @Test
    fun `duration buckets land on the documented edges`() {
        assertEquals("<1s", bucketMillis(0L))
        assertEquals("<1s", bucketMillis(999L))
        assertEquals("1-3s", bucketMillis(1_000L))
        assertEquals("1-3s", bucketMillis(2_999L))
        assertEquals("3-5s", bucketMillis(3_000L))
        assertEquals("3-5s", bucketMillis(4_999L))
        assertEquals("5-10s", bucketMillis(5_000L))
        assertEquals("5-10s", bucketMillis(9_999L))
        assertEquals("10-30s", bucketMillis(10_000L))
        assertEquals("10-30s", bucketMillis(29_999L))
        assertEquals("30s+", bucketMillis(30_000L))
    }

    @Test
    fun `a negative duration reports unknown rather than a fake fast one`() {
        // Latency is computed by subtracting timestamps. If a clock moves
        // backwards, "unknown" is honest; "<1s" would quietly claim the
        // fastest possible transcription.
        assertEquals("unknown", bucketMillis(-1L))
    }

    private val hour = 60L * 60 * 1_000

    @Test
    fun `reminder delay buckets land on the documented edges`() {
        assertEquals("<3h", bucketReminderDelay(3 * hour - 1))
        assertEquals("3-4h", bucketReminderDelay(3 * hour))
        assertEquals("3-4h", bucketReminderDelay(4 * hour - 1))
        assertEquals("4-6h", bucketReminderDelay(4 * hour))
        assertEquals("4-6h", bucketReminderDelay(6 * hour - 1))
        assertEquals("6-12h", bucketReminderDelay(6 * hour))
        assertEquals("6-12h", bucketReminderDelay(12 * hour - 1))
        assertEquals("12-24h", bucketReminderDelay(12 * hour))
        assertEquals("12-24h", bucketReminderDelay(24 * hour - 1))
        assertEquals("24h+", bucketReminderDelay(24 * hour))
    }

    @Test
    fun `a negative reminder delay falls into the earliest bucket`() {
        // Delay is computed by subtracting timestamps; a backwards clock must
        // not invent a new label. It lands where a too-early post belongs.
        assertEquals("<3h", bucketReminderDelay(-1L))
    }

    @Test
    fun `days ago buckets land on the documented edges`() {
        assertEquals("0", bucketDaysAgo(0))
        assertEquals("1", bucketDaysAgo(1))
        assertEquals("2-3", bucketDaysAgo(2))
        assertEquals("2-3", bucketDaysAgo(3))
        assertEquals("4-7", bucketDaysAgo(4))
        assertEquals("4-7", bucketDaysAgo(7))
        assertEquals("8-14", bucketDaysAgo(8))
        assertEquals("8-14", bucketDaysAgo(14))
        assertEquals("15-30", bucketDaysAgo(15))
        assertEquals("15-30", bucketDaysAgo(30))
        assertEquals("30+", bucketDaysAgo(31))
    }

    @Test
    fun `a negative age falls into the zero bucket`() {
        assertEquals("0", bucketDaysAgo(-1))
    }

    @Test
    fun `error days buckets land on the documented edges`() {
        assertEquals("<-7", bucketErrorDays(-8))
        assertEquals("-7..-3", bucketErrorDays(-7))
        assertEquals("-7..-3", bucketErrorDays(-3))
        assertEquals("-2..2", bucketErrorDays(-2))
        assertEquals("-2..2", bucketErrorDays(0))
        assertEquals("-2..2", bucketErrorDays(2))
        assertEquals("3..7", bucketErrorDays(3))
        assertEquals("3..7", bucketErrorDays(7))
        assertEquals(">7", bucketErrorDays(8))
    }
}
