package com.babegetthis.android.feature.gotthis.data

import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.HintOutcome
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.model.HintImpressionEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.domain.Days
import com.babegetthis.android.feature.gotthis.domain.GotThisIds
import com.babegetthis.android.feature.gotthis.domain.GotThisRules
import com.babegetthis.android.feature.gotthis.domain.ItemGroups
import com.babegetthis.android.feature.gotthis.domain.ItemJudge
import com.babegetthis.android.feature.gotthis.domain.JudgedPurchase
import com.babegetthis.android.feature.gotthis.domain.KeyStat
import com.babegetthis.android.feature.gotthis.domain.scoreLatestShop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.TimeZone

// Records when a hint was shown for an item and what the person then did with
// it, and scores each repurchase prediction. Events carry no item name or key;
// the key only ever lives in the local impression and mute rows. Telemetry must
// never break ticking or deleting, so a failure is reported as a non-fatal and
// swallowed; cancellation is rethrown.
class GotThisTracker(
    db: AppDatabase,
    private val analytics: AnalyticsRepository,
    private val access: GotThisAccess,
    private val prefs: GotThisPrefs,
    private val clock: ReminderClock,
    private val crashReporter: CrashReporter,
    private val tz: () -> TimeZone,
) {

    private val stateDao = db.gotThisStateDao()
    private val purchaseDao = db.purchaseDao()

    // One impression and one Shown event per item, however often the list redraws.
    suspend fun onHintsPresent(ownerId: String?, hints: Collection<BoughtHint>) = safely {
        if (!access.showsHints(ownerId) && !access.shadowsHints(ownerId)) return@safely
        val holdout = access.isHoldout(ownerId)
        val now = clock.now()
        hints.forEach { hint ->
            val accountId = ownerId ?: GotThisIds.GUEST_STATE_ID
            val row = HintImpressionEntity(hint.itemId, accountId, hint.canonicalKey, now, holdout)
            if (stateDao.insertImpressionIfAbsent(row) != ABSENT) {
                analytics.track(AnalyticsEvent.GotThisHintShown(hint.source, hint.daysAgo, holdout))
            }
        }
    }

    suspend fun onItemTicked(itemId: String, tick: TickResult?) = safely {
        stateDao.impression(itemId)?.let { impression ->
            analytics.track(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Ticked, impression.holdout))
            stateDao.deleteImpression(itemId)
            // In the holdout too, so both groups stop logging an item at the same point.
            countKept(impression)
        }
        if (tick != null && tick.recorded) scorePrediction(tick)
    }

    suspend fun onItemDeleted(itemId: String) = safely {
        val impression = stateDao.impression(itemId) ?: return@safely
        analytics.track(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Removed, impression.holdout))
        stateDao.deleteImpression(itemId)
        stateDao.mutes(impression.accountId).firstOrNull { it.itemKey == impression.canonicalKey }?.let {
            stateDao.upsertMute(it.copy(keptInARow = 0))
        }
    }

    suspend fun onHintMuted(itemId: String) = safely {
        val impression = stateDao.impression(itemId) ?: return@safely
        analytics.track(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Muted, impression.holdout))
        stateDao.deleteImpression(itemId)
        stateDao.upsertMute(
            ItemMuteEntity(impression.accountId, impression.canonicalKey, keptInARow = 0, mutedUntil = muteEnd()),
        )
    }

    // At most once per local day, however often the chips redraw.
    suspend fun onUsualsPresent(ownerId: String?, count: Int) = safely {
        if (!access.showsRunningLow(ownerId) && !access.shadowsRunningLow(ownerId)) return@safely
        val today = Days.epochDay(clock.now(), tz())
        if (prefs.lastUsualsShownDay == today) return@safely
        prefs.lastUsualsShownDay = today
        analytics.track(AnalyticsEvent.GotThisUsualsShown(count, access.isHoldout(ownerId)))
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashReporter.recordNonFatal(e, AppError.UnknownError(FAILED))
        }
    }

    private suspend fun countKept(impression: HintImpressionEntity) {
        val current = stateDao.mutes(impression.accountId).firstOrNull { it.itemKey == impression.canonicalKey }
        val kept = (current?.keptInARow ?: 0) + 1
        val row = if (kept >= GotThisRules.MUTE_AFTER_KEPT) {
            ItemMuteEntity(impression.accountId, impression.canonicalKey, keptInARow = 0, mutedUntil = muteEnd())
        } else {
            ItemMuteEntity(impression.accountId, impression.canonicalKey, kept, current?.mutedUntil)
        }
        stateDao.upsertMute(row)
    }

    private fun muteEnd(): Long = clock.now() + GotThisRules.MUTE_DAYS * Days.DAY_MILLIS

    // Scored on the phone that ticked: the history is read after the tick wrote its purchase.
    private suspend fun scorePrediction(tick: TickResult) {
        val stats = purchaseDao.keyStats(tick.ownerId).first().map {
            KeyStat(it.itemKey, it.purchases, it.lastBoughtAt)
        }
        val group = ItemGroups.groupFor(tick.itemKey, ItemGroups.build(stats)) ?: return
        val now = clock.now()
        val history = purchaseDao.liveForKeys(tick.ownerId, group.keys)
            .filter { it.boughtAt <= now }
            .map { JudgedPurchase(it.boughtAt, it.categoryId) }
        ItemJudge.scoreLatestShop(history, tz())?.let { analytics.track(AnalyticsEvent.GotThisPredictionScored(it)) }
    }

    private companion object {
        const val ABSENT = -1L
        const val FAILED = "Got This telemetry failed"
    }
}
