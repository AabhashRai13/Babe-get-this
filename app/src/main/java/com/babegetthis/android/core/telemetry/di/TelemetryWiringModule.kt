package com.babegetthis.android.core.telemetry.di

import android.os.SystemClock
import com.babegetthis.android.core.error.ErrorReporter
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.util.Throttle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Wiring that holds whichever vendor TelemetryModule installs. Kept apart from
// TelemetryModule on purpose: the instrumented suite replaces that module with
// recording fakes (@TestInstallIn in androidTest/testing/RecordingTelemetry.kt),
// and these bindings must survive that swap — they are built on the
// CrashReporter interface, so they follow the fake automatically.
@Module
@InstallIn(SingletonComponent::class)
object TelemetryWiringModule {

    // Connects SafeCall to crash reporting: every repository failure in the
    // app is offered here. recordNonFatal applies ErrorReportingPolicy
    // internally, so the filtering cannot be skipped by anything that
    // reaches this binding.
    @Provides
    @Singleton
    fun provideErrorReporter(crashReporter: CrashReporter): ErrorReporter = ErrorReporter(crashReporter::recordNonFatal)

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
