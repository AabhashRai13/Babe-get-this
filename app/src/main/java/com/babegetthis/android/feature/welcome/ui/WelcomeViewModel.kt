package com.babegetthis.android.feature.welcome.ui

import androidx.lifecycle.ViewModel
import com.babegetthis.android.core.onboarding.data.repository.WelcomeRepository
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.WelcomeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val welcome: WelcomeRepository,
    private val analytics: AnalyticsRepository,
) : ViewModel() {

    fun onContinueAsGuest() {
        welcome.markDone()
        analytics.track(AnalyticsEvent.WelcomeChoiceMade(WelcomeChoice.Guest))
    }

    // Welcome stays pending: backing out of Login lands here again.
    fun onSignIn() {
        analytics.track(AnalyticsEvent.WelcomeChoiceMade(WelcomeChoice.SignIn))
    }
}
