package com.babegetthis.android.core.telemetry.di

import android.os.SystemClock
import com.babegetthis.android.core.error.ErrorReporter
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.telemetry.data.CrashlyticsCrashReporter
import com.babegetthis.android.core.telemetry.data.FirebaseAnalyticsRepository
import com.babegetthis.android.core.util.Throttle
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Both implementations have @Inject constructors, so Hilt builds them; only
// the interface bindings need declaring. Same shape as PinModule.
//
// These two lines are the whole vendor commitment. Moving analytics to PostHog
// or crash reporting to Sentry means writing one implementation class and
// repointing one @Binds here — nothing above core/telemetry changes, because
// nothing above core/telemetry knows Firebase exists.
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(impl: FirebaseAnalyticsRepository): AnalyticsRepository

    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: CrashlyticsCrashReporter): CrashReporter

    companion object {
        // Connects SafeCall to crash reporting: every repository failure in the
        // app is offered here. recordNonFatal applies ErrorReportingPolicy
        // internally, so the filtering cannot be skipped by anything that
        // reaches this binding.
        @Provides
        @Singleton
        fun provideErrorReporter(crashReporter: CrashReporter): ErrorReporter =
            ErrorReporter(crashReporter::recordNonFatal)

        // How often TelemetryContext re-counts items and the sync queue for the
        // crash keys. 30s is long enough to collapse a burst of navigation into
        // one set of queries and short enough that a crash report is never
        // describing a materially different session.
        @Provides
        @Singleton
        fun provideDataShapeRefreshThrottle(): Throttle =
            Throttle(windowMillis = DATA_SHAPE_REFRESH_WINDOW_MS, now = SystemClock::elapsedRealtime)

        private const val DATA_SHAPE_REFRESH_WINDOW_MS = 30_000L
    }
}
