package com.babegetthis.android.testing

import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.sync.data.repository.SyncPointStore
import com.babegetthis.android.feature.gotthis.data.GotThisSync
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.remote.GotThisRemote
import com.babegetthis.android.feature.gotthis.data.remote.OwnPurchaseRow
import com.babegetthis.android.feature.gotthis.data.remote.PurchaseRow
import com.babegetthis.android.feature.gotthis.data.remote.TickEventRow
import com.babegetthis.android.feature.gotthis.data.remote.TickReply
import java.io.IOException

// In-memory GotThisPrefs with the same defaults and never-lower reset rule as
// the SharedPreferences implementation.
class FakeGotThisPrefs : GotThisPrefs {
    override var keyVersion = 0
    override var backfillDone = false
    override var guestHoldoutNumber = 50
    private val resets = mutableMapOf<String, Long>()
    private val clears = mutableMapOf<String, Long>()
    override var usualsHiddenUntil = 0L
    override var lastUsualsShownDay = 0L
    override var guestNotifiedWeek: String? = null
    override var lastSummaryWeek: String? = null
    override var hintsEnabled = true
    override var runningLowEnabled = true
    override var killed = false
    override var holdoutOn = true

    override fun lastAppliedReset(accountId: String): Long? = resets[accountId]

    override fun setLastAppliedReset(accountId: String, millis: Long) {
        val current = resets[accountId]
        if (current == null || millis > current) resets[accountId] = millis
    }

    override fun pendingClear(accountId: String): Long? = clears[accountId]

    override fun setPendingClear(accountId: String, millis: Long?) {
        if (millis == null) clears.remove(accountId) else clears[accountId] = millis
    }
}

// Records every call in order; each fail flag makes its method throw.
class FakeGotThisRemote : GotThisRemote {
    val calls = mutableListOf<String>()
    val recordedBatches = mutableListOf<List<TickEventRow>>()
    val uploadedBatches = mutableListOf<List<OwnPurchaseRow>>()
    val clearedAt = mutableListOf<String>()
    val fetchSince = mutableListOf<String?>()
    val claimedWeeks = mutableListOf<String>()
    var purchaseRows: List<PurchaseRow> = emptyList()
    var resetAt: String? = null
    var reply: (List<TickEventRow>) -> TickReply = { TickReply(it.map(TickEventRow::id), emptyList(), emptyList()) }
    var failClear = false
    var clearFailure: Throwable? = null
    var failRecord = false
    var failUpload = false
    var failFetch = false
    var claimResult = true
    var onRecordTicks: suspend () -> Unit = {}

    override suspend fun recordTicks(events: List<TickEventRow>): TickReply {
        calls += "recordTicks"
        if (failRecord) throw IOException("network down")
        onRecordTicks()
        recordedBatches += events
        return reply(events)
    }

    override suspend fun uploadOwnPurchases(rows: List<OwnPurchaseRow>) {
        calls += "uploadOwnPurchases"
        if (failUpload) throw IOException("network down")
        uploadedBatches += rows
    }

    override suspend fun clearHistory(clearedAtIso: String): String {
        calls += "clearHistory"
        clearFailure?.let { throw it }
        if (failClear) throw IOException("network down")
        clearedAt += clearedAtIso
        return clearedAtIso
    }

    override suspend fun fetchResetAt(): String? {
        calls += "fetchResetAt"
        if (failFetch) throw IOException("network down")
        return resetAt
    }

    override suspend fun fetchPurchases(sinceIso: String?): List<PurchaseRow> {
        calls += "fetchPurchases"
        if (failFetch) throw IOException("network down")
        fetchSince += sinceIso
        return purchaseRows.filter { sinceIso == null || it.updatedAt >= sinceIso }
    }

    override suspend fun claimRunningLowWeek(isoWeek: String): Boolean {
        calls += "claimRunningLowWeek"
        claimedWeeks += isoWeek
        return claimResult
    }
}

fun gotThisSyncFor(
    dbRule: InMemoryDatabaseRule,
    remote: GotThisRemote = FakeGotThisRemote(),
    prefs: GotThisPrefs = FakeGotThisPrefs(),
    syncPoints: SyncPointStore = FakeSyncPointStore(),
    clock: ReminderClock = ReminderClock { 0L },
) = GotThisSync(dbRule.db, remote, prefs, syncPoints, clock)
