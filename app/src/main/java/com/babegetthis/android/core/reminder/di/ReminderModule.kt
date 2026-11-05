package com.babegetthis.android.core.reminder.di

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.reminder.AndroidNotificationGate
import com.babegetthis.android.core.reminder.ForegroundState
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.ProcessForegroundState
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.reminder.ReminderKicker
import com.babegetthis.android.core.reminder.ReminderNotifier
import com.babegetthis.android.core.reminder.ReminderScheduler
import com.babegetthis.android.core.reminder.SystemReminderClock
import com.babegetthis.android.core.reminder.WorkEnqueuer
import com.babegetthis.android.core.reminder.WorkManagerEnqueuer
import com.babegetthis.android.core.reminder.data.local.ReminderContentReader
import com.babegetthis.android.core.reminder.data.local.ReminderEligibility
import com.babegetthis.android.core.reminder.data.local.ReminderPrefs
import com.babegetthis.android.core.reminder.data.local.SharedPrefsReminderPrefs
import com.babegetthis.android.core.telemetry.CrashReporter
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {

    @Provides
    @Singleton
    fun provideReminderPrefs(@ApplicationContext context: Context): ReminderPrefs = SharedPrefsReminderPrefs(context)

    @Provides
    @Singleton
    fun provideContentReader(db: AppDatabase): ReminderContentReader = ReminderContentReader(db)

    @Provides
    @Singleton
    fun provideEligibility(reader: ReminderContentReader): ReminderEligibility = ReminderEligibility(reader)

    @Provides
    @Singleton
    fun provideNotifier(@ApplicationContext context: Context): ReminderNotifier =
        ReminderNotifier(context, NotificationManagerCompat.from(context))

    @Provides
    @Singleton
    fun provideScheduler(
        enqueuer: WorkEnqueuer,
        prefs: ReminderPrefs,
        eligibility: ReminderEligibility,
        flags: FeatureFlagCache,
        gate: NotificationGate,
        crashReporter: CrashReporter,
        notifier: ReminderNotifier,
        clock: ReminderClock,
    ): ReminderScheduler = ReminderScheduler(
        enqueuer = enqueuer,
        prefs = prefs,
        eligibility = eligibility,
        flags = flags,
        gate = gate,
        crashReporter = crashReporter,
        notifier = notifier,
        clock = clock,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    // Same singleton instance as the scheduler, so the repositories' kicks and
    // the worker's ownership checks share one mutex.
    @Provides
    @Singleton
    fun provideKicker(scheduler: ReminderScheduler): ReminderKicker = scheduler
}

// The Android-bound pieces, split out so instrumented tests can swap them for a
// test WorkManager and a controllable foreground state without touching the rest.
@Module
@InstallIn(SingletonComponent::class)
object ReminderPlatformModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    // Lazy so building the enqueuer (during Application injection) does not
    // initialise WorkManager; see WorkManagerEnqueuer.
    @Provides
    @Singleton
    fun provideEnqueuer(workManager: Lazy<WorkManager>): WorkEnqueuer = WorkManagerEnqueuer(workManager)

    @Provides
    @Singleton
    fun provideGate(@ApplicationContext context: Context): NotificationGate = AndroidNotificationGate(context)

    @Provides
    @Singleton
    fun provideForeground(): ForegroundState = ProcessForegroundState()

    @Provides
    @Singleton
    fun provideClock(): ReminderClock = SystemReminderClock
}
