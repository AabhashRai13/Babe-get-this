package com.babegetthis.android.feature.gotthis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GotThisIdsTest {

    private val item = "11111111-1111-1111-1111-111111111111"
    private val user = "22222222-2222-2222-2222-222222222222"
    private val tickedAt = 1793527200000L

    @Test
    fun `tick id is the pinned md5 of item and time`() {
        assertEquals("ce0087de-31d1-fb82-a1e7-70abb1f8ce87", GotThisIds.tickId(item, tickedAt))
    }

    @Test
    fun `purchase id is the pinned md5 of user and tick`() {
        val tick = GotThisIds.tickId(item, tickedAt)
        assertEquals("c680a8ae-0383-3f3c-356c-4a16906dd012", GotThisIds.purchaseId(user, tick))
    }

    @Test
    fun `guest purchase id is the pinned md5 of guest and tick`() {
        val tick = GotThisIds.tickId(item, tickedAt)
        assertEquals("5ff805ee-b6fa-6688-3ae9-d990ea754c0f", GotThisIds.guestPurchaseId(tick))
    }

    @Test
    fun `holdout number is pinned for known accounts`() {
        assertEquals(12, GotThisIds.holdoutNumber(user))
        assertEquals(2, GotThisIds.holdoutNumber(item))
    }

    @Test
    fun `holdout number stays within 0 to 99`() {
        repeat(1000) {
            val n = GotThisIds.holdoutNumber(UUID.randomUUID().toString())
            assertTrue("out of range: $n", n in 0..99)
        }
    }
}
