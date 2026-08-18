package com.babegetthis.android.core.telemetry

import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric so the SharedPreferences are real. A mocked store would make the
// persistence assertions vacuous, and persistence is the whole point — an
// opt-out that forgets itself on restart is not an opt-out.
@RunWith(RobolectricTestRunner::class)
class TelemetryConsentTest {

    private val analytics = mockk<AnalyticsRepository>(relaxed = true)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private fun consent() = TelemetryConsent(
        ApplicationProvider.getApplicationContext(),
        analytics,
        crashReporter,
    )

    @Test
    fun `collection is on by default`() {
        // Opt-OUT model. If this ever flips to opt-in for GDPR, this test is
        // the one that should fail and force the decision to be deliberate.
        val consent = consent()

        assertTrue(consent.analyticsEnabled)
        assertTrue(consent.crashReportingEnabled)
    }

    @Test
    fun `opting out applies at the vendor, not just in our own state`() {
        // Suppressing track() calls above the SDK would not be an opt-out —
        // the SDK keeps collecting sessions and installation ids on its own.
        consent().setAnalyticsEnabled(false)

        verify { analytics.setCollectionEnabled(false) }
    }

    @Test
    fun `the choice survives a process restart`() {
        consent().setAnalyticsEnabled(false)

        // A brand-new instance, as after process death.
        assertFalse(consent().analyticsEnabled)
    }

    @Test
    fun `opting back in survives too`() {
        consent().setAnalyticsEnabled(false)
        consent().setAnalyticsEnabled(true)

        assertTrue(consent().analyticsEnabled)
    }

    @Test
    fun `the two switches are independent`() {
        // Crash reporting has a stronger legitimate-interest argument than
        // product analytics, so a user may reasonably keep one and drop the
        // other. One switch driving both would take that choice away.
        val consent = consent()

        consent.setAnalyticsEnabled(false)

        assertFalse(consent.analyticsEnabled)
        assertTrue(consent.crashReportingEnabled)
    }

    @Test
    fun `crash reporting opts out independently`() {
        val consent = consent()

        consent.setCrashReportingEnabled(false)

        verify { crashReporter.setCollectionEnabled(false) }
        assertTrue(consent.analyticsEnabled)
    }

    @Test
    fun `startup re-asserts the stored choice at the vendor`() {
        consent().setAnalyticsEnabled(false)

        consent().applyPersisted()

        // Redundant while Firebase persists its own flag — but "the vendor
        // remembers my users' opt-out" is not an assumption worth making
        // load-bearing on a privacy control.
        verify { analytics.setCollectionEnabled(false) }
        verify { crashReporter.setCollectionEnabled(true) }
    }
}
