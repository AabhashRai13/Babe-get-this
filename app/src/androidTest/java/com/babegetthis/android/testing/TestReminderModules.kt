package com.babegetthis.android.testing

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.babegetthis.android.core.reminder.AndroidNotificationGate
import com.babegetthis.android.core.reminder.ForegroundState
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.reminder.SystemReminderClock
import com.babegetthis.android.core.reminder.WorkEnqueuer
import com.babegetthis.android.core.reminder.WorkManagerEnqueuer
import com.babegetthis.android.core.reminder.di.ReminderPlatformModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

// --- reminders ---

// Swaps only the Android-bound pieces. WorkManager is the test build with the
// Hilt worker factory and a synchronous executor, so a journey can run the real
// ReminderWorker on demand. Foreground is pinned to false because the app is on
// screen throughout a journey, which would otherwise make the worker stay silent.
// The gate and clock stay real: the notification permission is granted per test.
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ReminderPlatformModule::class])
object TestReminderPlatformModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context, factory: HiltWorkerFactory): WorkManager {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setWorkerFactory(factory)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideEnqueuer(workManager: WorkManager): WorkEnqueuer = WorkManagerEnqueuer(workManager)

    @Provides
    @Singleton
    fun provideGate(@ApplicationContext context: Context): NotificationGate = AndroidNotificationGate(context)

    @Provides
    @Singleton
    fun provideForeground(): ForegroundState = ForegroundState { false }

    @Provides
    @Singleton
    fun provideClock(): ReminderClock = SystemReminderClock
}
