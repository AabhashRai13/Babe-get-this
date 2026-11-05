package com.babegetthis.android.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.data.di.ApplicationScope
import com.babegetthis.android.core.pin.data.PinRepository
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.PermissionState
import com.babegetthis.android.core.reminder.ReminderScheduler
import com.babegetthis.android.core.reminder.data.local.ReminderPrefs
import com.babegetthis.android.core.telemetry.TelemetryConsent
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the single reminders switch shows. [Blocked] is never shown as on. */
enum class ReminderRowState { On, Off, Blocked }

/** One-shot requests for the screen, which owns the permission launcher and the system intent. */
sealed interface SettingsEvent {
    data object RequestNotificationPermission : SettingsEvent
    data object OpenNotificationSettings : SettingsEvent
}

// Settings depends on PinRepository (PIN state) and ShoppingListRepository
// (unlock-all on removal, and the locked-count warning). Deliberately NOT on
// AuthStateManager — Settings must work signed out.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    pinRepository: PinRepository,
    private val listRepository: ShoppingListRepository,
    private val consent: TelemetryConsent,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val reminderScheduler: ReminderScheduler,
    private val reminderPrefs: ReminderPrefs,
    private val notificationGate: NotificationGate,
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

    // The permission state comes from the composition (it needs an Activity), so
    // it is pushed in. Granted is the neutral default until the screen reports.
    private var permissionState = PermissionState.Granted

    private val _reminderRow = MutableStateFlow(ReminderRowState.Off)
    val reminderRow: StateFlow<ReminderRowState> = _reminderRow.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        refreshReminderRow()
    }

    fun permissionRequested(): Boolean = reminderScheduler.permissionRequested()

    // The user may have flipped the OS notification switch while away; the gate is re-read in the refresh.
    fun onResumed() = refreshReminderRow()

    fun updatePermissionState(state: PermissionState) {
        permissionState = state
        refreshReminderRow()
    }

    // Enabling from Settings does not arm a list: setEnabled(true) is the whole effect.
    fun onReminderSwitch(checked: Boolean) {
        if (!checked) {
            enableReminders(false)
            return
        }
        // Branch on the state as it is now, not as last computed: the OS
        // switch may have moved since the row was drawn.
        refreshReminderRow()
        when {
            _reminderRow.value == ReminderRowState.Blocked ->
                emitEvent(SettingsEvent.OpenNotificationSettings)
            permissionState == PermissionState.Requestable -> {
                reminderScheduler.onPermissionRequested()
                emitEvent(SettingsEvent.RequestNotificationPermission)
            }
            else -> enableReminders(true)
        }
    }

    // A denial is the items-screen flow's concern to snooze; Settings only re-reads.
    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) enableReminders(true) else refreshReminderRow()
    }

    private fun emitEvent(event: SettingsEvent) {
        viewModelScope.launch { _events.emit(event) }
    }

    private fun enableReminders(enabled: Boolean) {
        viewModelScope.launch {
            reminderScheduler.setEnabled(enabled)
            refreshReminderRow()
        }
    }

    // A closed OS gate means Blocked only when the runtime permission is not
    // the reason: on API 33+ areNotificationsEnabled() is false whenever
    // POST_NOTIFICATIONS is not granted, so with Requestable the gate is closed
    // by the missing grant and the row must stay askable (Off), not Blocked.
    private fun refreshReminderRow() {
        val permissionHeld = permissionState == PermissionState.Granted ||
            permissionState == PermissionState.NotRequired
        _reminderRow.value = when {
            permissionState == PermissionState.PermanentlyRefused ||
                (!notificationGate.notificationsEnabled() && permissionHeld) ->
                ReminderRowState.Blocked
            reminderPrefs.enabled && permissionHeld -> ReminderRowState.On
            else -> ReminderRowState.Off
        }
    }
}
