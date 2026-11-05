package com.babegetthis.android.feature.welcome.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.onboarding.data.repository.WelcomeRepository
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.WelcomeChoice
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WelcomeViewModelTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("welcome-vm-test", Context.MODE_PRIVATE)
    private val welcome = WelcomeRepository(prefs) { false }
    private val analytics = mockk<AnalyticsRepository>(relaxed = true)
    private val viewModel = WelcomeViewModel(welcome, analytics)

    @After
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `continue as guest marks Welcome done and reports guest`() {
        viewModel.onContinueAsGuest()

        assertFalse(welcome.shouldShowWelcome(AuthState.Unauthenticated))
        verify { analytics.track(AnalyticsEvent.WelcomeChoiceMade(WelcomeChoice.Guest)) }
    }

    // Backing out of Login must land on Welcome again, so tapping Sign in
    // records the choice but not that Welcome is done.
    @Test
    fun `sign in reports sign_in and leaves Welcome pending`() {
        viewModel.onSignIn()

        assertTrue(welcome.shouldShowWelcome(AuthState.Unauthenticated))
        verify { analytics.track(AnalyticsEvent.WelcomeChoiceMade(WelcomeChoice.SignIn)) }
    }
}
