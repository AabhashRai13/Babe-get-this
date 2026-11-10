package com.babegetthis.android.feature.gotthis.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.reminder.ReminderTarget
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.feature.gotthis.data.GotThisAccess
import com.babegetthis.android.feature.gotthis.data.GotThisRepository
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.model.ReminderFollowUpEntity
import com.babegetthis.android.feature.gotthis.data.remote.GotThisRemote
import com.babegetthis.android.feature.gotthis.domain.Days
import com.babegetthis.android.feature.gotthis.domain.GotThisRules
import com.babegetthis.android.feature.gotthis.domain.ItemKey
import com.babegetthis.android.feature.gotthis.domain.ShopDay
import com.babegetthis.android.feature.gotthis.domain.ZoneProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlin.math.max

/**
 * The daily check behind the weekly "Running low?" notification. It scores
 * earlier notifications, sends the weekly summary and then decides whether to
 * post. Every path returns [Result.success]: a missed evening is not retried.
 */
@HiltWorker
class RunningLowWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val repository: GotThisRepository,
    private val prefs: GotThisPrefs,
    private val access: GotThisAccess,
    private val remote: GotThisRemote,
    private val authStateManager: AuthStateManager,
    private val notifier: RunningLowNotifier,
    private val notificationGate: NotificationGate,
    private val analytics: AnalyticsRepository,
    private val crashReporter: CrashReporter,
    private val clock: ReminderClock,
    private val zone: ZoneProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            runChecks()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashReporter.recordNonFatal(e, AppError.UnknownError(FAILED))
        }
        return Result.success()
    }

    private suspend fun runChecks() {
        val owner = authStateManager.dataOwnerId()
        val now = clock.now()
        val tz = zone.current()
        val week = Days.isoWeek(now, tz)

        scoreFollowUps(owner, now)
        sendSummary(owner, week)
        if (prefs.killed || !prefs.runningLowEnabled) return

        val due = repository.dueItems(owner, withinDays = 1)
        if (due.isEmpty()) return
        val usual = ShopDay.usualWeekday(repository.purchaseDays(owner), now, tz)
        if (usual != null && Days.weekday(Days.epochDay(now, tz) + 1) != usual) return

        val holdout = access.isHoldout(owner)
        if (!holdout && !notificationGate.notificationsEnabled()) return
        if (!claimWeek(owner, week)) return

        val named = due.take(GotThisRules.MAX_NOTIFICATION_NAMES)
        if (holdout) {
            analytics.track(AnalyticsEvent.GotThisReminderPosted(due.size, holdout = true))
        } else {
            val target = db.shoppingListDao().mostRecentlyEdited(owner)?.let { ReminderTarget(it.id, it.name) }
            notifier.post(named.map { it.name }, max(0, due.size - named.size), target)
            analytics.track(AnalyticsEvent.GotThisReminderPosted(due.size, holdout = false))
        }
        db.gotThisStateDao().insertFollowUp(
            ReminderFollowUpEntity(
                accountId = owner.orEmpty(),
                isoWeek = week,
                postedAt = now,
                itemKeys = named.joinToString("\n") { it.canonicalKey },
                holdout = holdout,
            ),
        )
    }

    // Seven days after a notification: did the person add any item it named?
    private suspend fun scoreFollowUps(owner: String?, now: Long) {
        val stateDao = db.gotThisStateDao()
        stateDao.followUps(owner.orEmpty())
            .filter { it.postedAt + FOLLOW_UP_MILLIS <= now }
            .forEach { row ->
                val keys = row.itemKeys.split("\n")
                val added = db.shoppingItemDao().namesCreatedSince(owner, row.postedAt)
                    .any { name -> keys.any { ItemKey.matches(ItemKey.of(name), it) } }
                analytics.track(AnalyticsEvent.GotThisReminderFollowUp(added, row.holdout))
                stateDao.deleteFollowUp(row.accountId, row.isoWeek)
            }
    }

    private suspend fun sendSummary(owner: String?, week: String) {
        if (prefs.lastSummaryWeek == week) return
        val counts = repository.summaryCounts(owner)
        analytics.track(AnalyticsEvent.GotThisWeeklySummary(counts.regulars, counts.purchases))
        prefs.lastSummaryWeek = week
    }

    // At most one notification a week per person. A guest's claim lives on the
    // phone; an account's is made on the server so two phones do not both
    // notify. Any doubt (no live session, offline, already claimed) is silence.
    private suspend fun claimWeek(owner: String?, week: String): Boolean {
        if (owner == null) {
            if (prefs.guestNotifiedWeek == week) return false
            prefs.guestNotifiedWeek = week
            return true
        }
        if (authStateManager.liveSessionUserId() != owner) return false
        return try {
            remote.claimRunningLowWeek(week)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Being offline is normal: a breadcrumb, not a non-fatal.
            crashReporter.breadcrumb("running low claim failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private companion object {
        const val FAILED = "running low worker"
        const val FOLLOW_UP_MILLIS = GotThisRules.FOLLOW_UP_DAYS * 86_400_000L
    }
}
