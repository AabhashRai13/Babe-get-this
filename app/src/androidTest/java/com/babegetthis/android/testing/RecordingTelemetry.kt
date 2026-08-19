package com.babegetthis.android.testing

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.CrashKey
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.telemetry.data.AnalyticsEventMapper
import com.babegetthis.android.core.telemetry.data.ErrorReportingPolicy
import com.babegetthis.android.core.telemetry.data.MappedEvent
import com.babegetthis.android.core.telemetry.di.TelemetryModule
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

// Telemetry fakes for the end-to-end suite.
//
// Two reasons this module has to exist. The first is the rule the suite already
// follows and this would otherwise have broken: docs/running-tests.md promises
// that instrumented tests reach no network. Without this replacement every e2e
// run would fire real events into the babe-get-this-stg project and open real
// Crashlytics sessions — polluting the exact project that exists to verify
// production wiring.
//
// The second is that a fake which RECORDS is worth more than one that discards.
// It turns "no item name ever leaves the device" from a claim someone checks by
// squinting at DebugView into an assertion a test can fail on.
//
// Deliberately runs the REAL AnalyticsEventMapper and the REAL
// ErrorReportingPolicy. Faking those would test the fake: the mapper is what
// decides the payload a user's data ends up in, and the policy is what decides
// whether an error is transmitted at all. Only the vendor SDK call is replaced.
class RecordingAnalytics : AnalyticsRepository {

    val events = mutableListOf<AnalyticsEvent>()
    val mapped = mutableListOf<MappedEvent>()
    var userId: String? = null
    // NOT named `collectionEnabled`: that property's generated setter has the
    // same JVM signature as the interface method it would record.
    var lastCollectionEnabled: Boolean = true

    override fun track(event: AnalyticsEvent) {
        events += event
        // Through the real mapper, so assertions see the actual wire payload
        // rather than the pre-mapping event object.
        mapped += AnalyticsEventMapper.map(event)
    }

    override fun setUser(userId: String?) {
        this.userId = userId
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        lastCollectionEnabled = enabled
    }

    fun names(): List<String> = mapped.map { it.name }

    // Every string that would be transmitted: event names, parameter names and
    // parameter values. The privacy assertions run against this.
    fun allTransmittedStrings(): List<String> =
        mapped.flatMap { listOf(it.name) + it.text.keys + it.text.values + it.numbers.keys }

    fun clear() {
        events.clear()
        mapped.clear()
    }
}

class RecordingCrashReporter : CrashReporter {

    val breadcrumbs = mutableListOf<String>()
    val keys = mutableMapOf<CrashKey, String>()

    // Only what SURVIVES the policy — the fake applies it exactly as
    // CrashlyticsCrashReporter does, so a test asserting "offline produces no
    // reports" is asserting against the real rule.
    val reported = mutableListOf<Pair<Throwable, AppError>>()

    // Everything offered, filtered or not, so a test can tell "the policy
    // dropped it" from "nothing ever reached the reporter".
    val offered = mutableListOf<AppError>()

    var userId: String? = null
    // NOT named `collectionEnabled`: that property's generated setter has the
    // same JVM signature as the interface method it would record.
    var lastCollectionEnabled: Boolean = true

    override fun breadcrumb(message: String) {
        breadcrumbs += message
    }

    override fun setKey(key: CrashKey, value: String) {
        keys[key] = value
    }

    override fun recordNonFatal(throwable: Throwable, error: AppError) {
        offered += error
        if (!ErrorReportingPolicy.shouldReport(error)) return
        reported += throwable to error
    }

    override fun setUser(userId: String?) {
        this.userId = userId
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        lastCollectionEnabled = enabled
    }

    fun clear() {
        breadcrumbs.clear()
        keys.clear()
        reported.clear()
        offered.clear()
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [TelemetryModule::class])
object TestTelemetryModule {

    // Singletons so a test can inject the same instance the app is writing to.
    @Provides
    @Singleton
    fun provideRecordingAnalytics(): RecordingAnalytics = RecordingAnalytics()

    @Provides
    @Singleton
    fun provideRecordingCrashReporter(): RecordingCrashReporter = RecordingCrashReporter()

    @Provides
    @Singleton
    fun provideAnalyticsRepository(impl: RecordingAnalytics): AnalyticsRepository = impl

    @Provides
    @Singleton
    fun provideCrashReporter(impl: RecordingCrashReporter): CrashReporter = impl
}
