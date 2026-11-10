package com.babegetthis.android.feature.gotthis.data

import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.HintOutcome
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.model.HintImpressionEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.domain.Days
import com.babegetthis.android.feature.gotthis.domain.GotThisRules
import com.babegetthis.android.feature.gotthis.domain.ItemGroups
import com.babegetthis.android.feature.gotthis.domain.ItemJudge
import com.babegetthis.android.feature.gotthis.domain.JudgedPurchase
import com.babegetthis.android.feature.gotthis.domain.KeyStat
import com.babegetthis.android.feature.gotthis.domain.scoreLatestShop
import kotlinx.coroutines.flow.first
import java.util.TimeZone

// Records when a hint was shown for an item and what the person then did with
// it, and scores each repurchase prediction. Events carry no item name or key;
// the key only ever lives in the local impression and mute rows.
class GotThisTracker(
    db: AppDatabase,
    private val analytics: AnalyticsRepository,
    private val access: GotThisAccess,
    private val prefs: GotThisPrefs,
    private val clock: ReminderClock,
    private val tz: () -> TimeZone,
) {

    private val stateDao = db.gotThisStateDao()
    private val purchaseDao = db.purchaseDao()

    // One impression and one Shown event per item, however often the list redraws.
    suspend fun onHintsPresent(ownerId: String?, hints: Collection<BoughtHint>) {
        if (!access.showsHints(ownerId) && !access.shadows(ownerId)) return
        val holdout = access.isHoldout(ownerId)
        val now = clock.now()
        hints.forEach { hint ->
            val row = HintImpressionEntity(hint.itemId, ownerId ?: GUEST_STATE_ID, hint.canonicalKey, now, holdout)
            if (stateDao.insertImpressionIfAbsent(row) != ABSENT) {
                analytics.track(AnalyticsEvent.GotThisHintShown(hint.source, hint.daysAgo, holdout))
            }
        }
    }

    suspend fun onItemTicked(itemId: String, tick: TickResult?) {
        stateDao.impression(itemId)?.let { impression ->
            analytics.track(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Ticked, impression.holdout))
            stateDao.deleteImpression(itemId)
            if (!impression.holdout) countKept(impression)
        }
        if (tick != null && tick.recorded) scorePrediction(tick)
    }

    suspend fun onItemDeleted(itemId: String) {
        val impression = stateDao.impression(itemId) ?: return
        analytics.track(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Removed, impression.holdout))
        stateDao.deleteImpression(itemId)
        if (impression.holdout) return
        stateDao.mutes(impression.accountId).firstOrNull { it.itemKey == impression.canonicalKey }?.let {
            stateDao.upsertMute(it.copy(keptInARow = 0))
        }
    }

    suspend fun onHintMuted(itemId: String) {
        val impression = stateDao.impression(itemId) ?: return
        analytics.track(AnalyticsEvent.GotThisHintOutcome(HintOutcome.Muted, impression.holdout))
        stateDao.deleteImpression(itemId)
        stateDao.upsertMute(
            ItemMuteEntity(impression.accountId, impression.canonicalKey, keptInARow = 0, mutedUntil = muteEnd()),
        )
    }

    // At most once per local day, however often the chips redraw.
    suspend fun onUsualsPresent(ownerId: String?, count: Int) {
        if (!access.showsRunningLow(ownerId) && !access.shadows(ownerId)) return
        val today = Days.epochDay(clock.now(), tz())
        if (prefs.lastUsualsShownDay == today) return
        prefs.lastUsualsShownDay = today
        analytics.track(AnalyticsEvent.GotThisUsualsShown(count, access.isHoldout(ownerId)))
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

    private fun muteEnd(): Long = clock.now() + GotThisRules.MUTE_DAYS * DAY_MILLIS

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
        const val DAY_MILLIS = 86_400_000L
        const val GUEST_STATE_ID = ""
    }
}
