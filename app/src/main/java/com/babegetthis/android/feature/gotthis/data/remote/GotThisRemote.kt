package com.babegetthis.android.feature.gotthis.data.remote

interface GotThisRemote {
    suspend fun recordTicks(events: List<TickEventRow>): TickReply

    suspend fun uploadOwnPurchases(rows: List<OwnPurchaseRow>)

    /** Returns the resulting reset_at. */
    suspend fun clearHistory(clearedAtIso: String): String

    suspend fun fetchResetAt(): String?

    /** Complete result, paged on (updated_at, id), updated_at >= [sinceIso]. */
    suspend fun fetchPurchases(sinceIso: String?): List<PurchaseRow>

    suspend fun claimRunningLowWeek(isoWeek: String): Boolean
}
