package com.babegetthis.android.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.data.di.ApplicationScope
import com.babegetthis.android.core.pin.data.PinRepository
import com.babegetthis.android.core.telemetry.TelemetryConsent
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Settings depends on PinRepository (PIN state) and ShoppingListRepository
// (unlock-all on removal, and the locked-count warning). Deliberately NOT on
// AuthStateManager — Settings must work signed out.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    pinRepository: PinRepository,
    private val listRepository: ShoppingListRepository,
    private val consent: TelemetryConsent,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    val pinExists: StateFlow<Boolean> = pinRepository.pinExists

    // Seeded from TelemetryConsent, which reads its mirror of the SDK flags.
    // Held as state here so the switches stay responsive — the vendor setters
    // are fire-and-forget and report nothing back.
    private val _analyticsEnabled = MutableStateFlow(consent.analyticsEnabled)
    val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()

    private val _crashReportingEnabled = MutableStateFlow(consent.crashReportingEnabled)
    val crashReportingEnabled: StateFlow<Boolean> = _crashReportingEnabled.asStateFlow()

    // Two switches rather than one. Crash reporting is defensible on legitimate
    // interest in a way product analytics is not, so a user may reasonably want
    // to keep the first and drop the second.
    fun setAnalyticsEnabled(enabled: Boolean) {
        consent.setAnalyticsEnabled(enabled)
        _analyticsEnabled.value = enabled
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        consent.setCrashReportingEnabled(enabled)
        _crashReportingEnabled.value = enabled
    }

    private val _lockedCount = MutableStateFlow(0)
    val lockedCount: StateFlow<Int> = _lockedCount.asStateFlow()

    fun refreshLockedCount() {
        viewModelScope.launch { _lockedCount.value = listRepository.lockedCount() }
    }

    // Called after the PIN has been removed — clears the now-meaningless isLocked
    // flag on every row so cards stop showing a lock badge.
    //
    // applicationScope, NOT viewModelScope: the user dismisses this dialog and
    // very often leaves Settings in the same breath, which cancels viewModelScope
    // before the write lands.
    //
    // Access is NOT gated on this succeeding — ShoppingItemsViewModel.isLocked
    // requires pinExists as well as the row flag, so a list whose flag never got
    // cleared is still fully reachable. That is deliberate: this used to be the
    // only thing standing between the user and a permanently inaccessible list.
    // A failure here is now cosmetic, so surfacing it would be noise; the next
    // successful removal clears it.
    fun onPinRemoved() {
        applicationScope.launch {
            listRepository.unlockAll()
            _lockedCount.value = 0
        }
    }
}
