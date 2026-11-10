package com.babegetthis.android.feature.gotthis.domain

import java.security.MessageDigest

// Deterministic ids: the same tick always yields the same ids on every device,
// so a replayed outbox event is an upsert rather than a duplicate. Formatted
// like a UUID (8-4-4-4-12) but with no version bits.
object GotThisIds {

    fun tickId(itemId: String, tickedAtMillis: Long): String = md5Uuid("$itemId:$tickedAtMillis")

    fun purchaseId(userId: String, tickId: String): String = md5Uuid("$userId:$tickId")

    fun guestPurchaseId(tickId: String): String = md5Uuid("guest:$tickId")

    // First 4 MD5 bytes as an unsigned big-endian int, mod 100.
    fun holdoutNumber(accountId: String): Int {
        val d = md5(accountId)
        var n = 0L
        for (i in 0 until 4) n = (n shl 8) or (d[i].toLong() and 0xFF)
        return (n % 100).toInt()
    }

    private fun md5(text: String): ByteArray = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))

    private fun md5Uuid(text: String): String {
        val hex = md5(text).joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }
}
