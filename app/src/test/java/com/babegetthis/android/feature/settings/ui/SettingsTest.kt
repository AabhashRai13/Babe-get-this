package com.babegetthis.android.feature.settings.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.pin.data.PinRepository
import com.babegetthis.android.core.reminder.NotificationGate
import com.babegetthis.android.core.reminder.PermissionState
import com.babegetthis.android.core.reminder.ReminderScheduler
import com.babegetthis.android.core.telemetry.TelemetryConsent
import com.babegetthis.android.core.ui.components.SettingsRow
import com.babegetthis.android.feature.gotthis.data.GotThisRepository
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.FakeNotificationGate
import com.babegetthis.android.testing.FakeReminderPrefs
import com.babegetthis.android.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val pinRepository = mockk<PinRepository>(relaxed = true)
    private val listRepository = mockk<ShoppingListRepository>(relaxed = true)
    private val pinExists = MutableStateFlow(false)

    // relaxed returns false for both consent getters, so the switches start
    // OFF in tests. Production defaults them on — tests that care stub it.
    private val consent = mockk<TelemetryConsent>(relaxed = true)

    private val reminderScheduler = mockk<ReminderScheduler>(relaxed = true)
    private val reminderPrefs = FakeReminderPrefs()
    private val notificationGate = FakeNotificationGate()
    private val gotThisPrefs = FakeGotThisPrefs()
    private val gotThisRepository = mockk<GotThisRepository>(relaxed = true)
    private val authStateManager = mockk<AuthStateManager>(relaxed = true)

    private fun viewModel(): SettingsViewModel {
        every { pinRepository.pinExists } returns pinExists
        return SettingsViewModel(
            pinRepository,
            listRepository,
            consent,
            CoroutineScope(UnconfinedTestDispatcher()),
            reminderScheduler,
            reminderPrefs,
            notificationGate,
            gotThisPrefs,
            gotThisRepository,
            authStateManager,
        )
    }

    @Test
    fun `pinExists mirrors the repository`() = runTest {
        val vm = viewModel()
        assertTrue(!vm.pinExists.value)

        pinExists.value = true

        assertTrue(vm.pinExists.value)
    }

    @Test
    fun `locked count starts at zero`() = runTest {
        assertEquals(0, viewModel().lockedCount.value)
    }

    @Test
    fun `refreshLockedCount reads the repository`() = runTest {
        coEvery { listRepository.lockedCount() } returns 3
        val vm = viewModel()

        vm.refreshLockedCount()

        assertEquals(3, vm.lockedCount.value)
    }

    @Test
    fun `refreshLockedCount picks up a later change`() = runTest {
        coEvery { listRepository.lockedCount() } returns 3
        val vm = viewModel()
        vm.refreshLockedCount()

        coEvery { listRepository.lockedCount() } returns 0
        vm.refreshLockedCount()

        assertEquals(0, vm.lockedCount.value)
    }

    @Test
    fun `onPinRemoved unlocks every list`() = runTest {
        coEvery { listRepository.unlockAll() } returns Result.Success(Unit)
        val vm = viewModel()

        vm.onPinRemoved()

        coVerify { listRepository.unlockAll() }
    }

    @Test
    fun `onPinRemoved zeroes the locked count`() = runTest {
        coEvery { listRepository.lockedCount() } returns 4
        coEvery { listRepository.unlockAll() } returns Result.Success(Unit)
        val vm = viewModel()
        vm.refreshLockedCount()

        vm.onPinRemoved()

        assertEquals(0, vm.lockedCount.value)
    }

    // The unlock runs on applicationScope, so it is not tied to the Settings
    // screen surviving. Access no longer depends on it landing either — isLocked
    // requires pinExists — but the row flags should still get cleared.
    @Test
    fun `onPinRemoved survives the viewModel scope being irrelevant`() = runTest {
        coEvery { listRepository.unlockAll() } returns Result.Error(
            com.babegetthis.android.core.error.AppError.DatabaseError(),
        )
        val vm = viewModel()

        // A failed unlock is cosmetic now, so it must not blow up.
        vm.onPinRemoved()

        coVerify { listRepository.unlockAll() }
    }

    // -- Telemetry consent --

    @Test
    fun `the switches start from the stored choice, not from a default`() = runTest {
        every { consent.analyticsEnabled } returns false
        every { consent.crashReportingEnabled } returns true

        val vm = viewModel()

        assertTrue(!vm.analyticsEnabled.value)
        assertTrue(vm.crashReportingEnabled.value)
    }

    @Test
    fun `opting out of analytics reaches the consent store`() = runTest {
        val vm = viewModel()

        vm.setAnalyticsEnabled(false)

        verify { consent.setAnalyticsEnabled(false) }
        assertTrue(!vm.analyticsEnabled.value)
    }

    @Test
    fun `opting out of crash reporting reaches the consent store`() = runTest {
        val vm = viewModel()

        vm.setCrashReportingEnabled(false)

        verify { consent.setCrashReportingEnabled(false) }
        assertTrue(!vm.crashReportingEnabled.value)
    }

    @Test
    fun `the two switches are independent`() = runTest {
        // Crash reporting stands on different footing to product analytics, so
        // dropping one must never drop the other.
        val vm = viewModel()

        vm.setAnalyticsEnabled(false)

        verify(exactly = 0) { consent.setCrashReportingEnabled(any()) }
    }

    @Test
    fun `opting back in reaches the consent store too`() = runTest {
        val vm = viewModel()

        vm.setAnalyticsEnabled(false)
        vm.setAnalyticsEnabled(true)

        verify { consent.setAnalyticsEnabled(true) }
        assertTrue(vm.analyticsEnabled.value)
    }

    @Test
    fun `the reminder row is On only with the preference and permission`() = runTest {
        val vm = viewModel()

        reminderPrefs.enabled = true
        vm.updatePermissionState(PermissionState.Granted)
        assertEquals(ReminderRowState.On, vm.reminderRow.value)

        vm.updatePermissionState(PermissionState.NotRequired)
        assertEquals(ReminderRowState.On, vm.reminderRow.value)

        reminderPrefs.enabled = false
        vm.updatePermissionState(PermissionState.Granted)
        assertEquals(ReminderRowState.Off, vm.reminderRow.value)

        reminderPrefs.enabled = true
        vm.updatePermissionState(PermissionState.Requestable)
        assertEquals(ReminderRowState.Off, vm.reminderRow.value)
    }

    @Test
    fun `permanently refused or os-disabled reads Blocked`() = runTest {
        val vm = viewModel()
        reminderPrefs.enabled = true

        vm.updatePermissionState(PermissionState.PermanentlyRefused)
        assertEquals(ReminderRowState.Blocked, vm.reminderRow.value)

        notificationGate.enabled = false
        vm.updatePermissionState(PermissionState.Granted)
        assertEquals(ReminderRowState.Blocked, vm.reminderRow.value)
    }

    // On API 33+ areNotificationsEnabled() is false whenever POST_NOTIFICATIONS
    // is not granted, so a never-asked user has the gate closed. That must read
    // as Off (askable), not Blocked, or the direct request can never run.
    @Test
    fun `gate closed with Requestable reads Off and switching on asks for permission`() = runTest {
        notificationGate.enabled = false
        reminderPrefs.enabled = true
        val order = mutableListOf<String>()
        every { reminderScheduler.onPermissionRequested() } answers { order += "requested" }
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)
        assertEquals(ReminderRowState.Off, vm.reminderRow.value)

        vm.events.test {
            vm.onReminderSwitch(true)

            assertEquals(SettingsEvent.RequestNotificationPermission, awaitItem())
            order += "event"
        }
        assertEquals(listOf("requested", "event"), order)
        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
    }

    @Test
    fun `gate closed with Granted reads Blocked`() = runTest {
        notificationGate.enabled = false
        reminderPrefs.enabled = true
        val vm = viewModel()

        vm.updatePermissionState(PermissionState.Granted)

        assertEquals(ReminderRowState.Blocked, vm.reminderRow.value)
    }

    @Test
    fun `gate closed with NotRequired reads Blocked`() = runTest {
        notificationGate.enabled = false
        reminderPrefs.enabled = true
        val vm = viewModel()

        vm.updatePermissionState(PermissionState.NotRequired)

        assertEquals(ReminderRowState.Blocked, vm.reminderRow.value)
    }

    @Test
    fun `gate open with Requestable reads Off`() = runTest {
        notificationGate.enabled = true
        reminderPrefs.enabled = true
        val vm = viewModel()

        vm.updatePermissionState(PermissionState.Requestable)

        assertEquals(ReminderRowState.Off, vm.reminderRow.value)
    }

    // The switch branches on the state as it is now, not as last pushed: the
    // user may have turned notifications off in system settings meanwhile.
    @Test
    fun `switching on re-reads the gate before choosing a branch`() = runTest {
        reminderPrefs.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Granted)
        assertEquals(ReminderRowState.Off, vm.reminderRow.value)

        notificationGate.enabled = false
        vm.events.test {
            vm.onReminderSwitch(true)

            assertEquals(SettingsEvent.OpenNotificationSettings, awaitItem())
        }
        assertEquals(ReminderRowState.Blocked, vm.reminderRow.value)
        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
    }

    @Test
    fun `switching off disables the scheduler`() = runTest {
        val vm = viewModel()

        vm.onReminderSwitch(false)

        coVerify { reminderScheduler.setEnabled(false) }
    }

    @Test
    fun `switching off refreshes the row from the preference`() = runTest {
        reminderPrefs.enabled = true
        val vm = viewModel()
        assertEquals(ReminderRowState.On, vm.reminderRow.value)
        coEvery { reminderScheduler.setEnabled(false) } answers { reminderPrefs.enabled = false }

        vm.onReminderSwitch(false)

        assertEquals(ReminderRowState.Off, vm.reminderRow.value)
    }

    @Test
    fun `switching on when Requestable records the request and asks for permission`() = runTest {
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)

        vm.events.test {
            vm.onReminderSwitch(true)

            assertEquals(SettingsEvent.RequestNotificationPermission, awaitItem())
        }
        verify { reminderScheduler.onPermissionRequested() }
        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
    }

    @Test
    fun `switching on when Blocked opens system settings`() = runTest {
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.PermanentlyRefused)

        vm.events.test {
            vm.onReminderSwitch(true)

            assertEquals(SettingsEvent.OpenNotificationSettings, awaitItem())
        }
        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
        verify(exactly = 0) { reminderScheduler.onPermissionRequested() }
    }

    @Test
    fun `switching on when the os has notifications off opens system settings`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()

        vm.events.test {
            vm.onReminderSwitch(true)

            assertEquals(SettingsEvent.OpenNotificationSettings, awaitItem())
        }
    }

    @Test
    fun `switching on when Granted enables`() = runTest {
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Granted)

        vm.onReminderSwitch(true)

        coVerify { reminderScheduler.setEnabled(true) }
        verify(exactly = 0) { reminderScheduler.onPermissionRequested() }
    }

    @Test
    fun `a granted permission result enables`() = runTest {
        reminderPrefs.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Granted)
        coEvery { reminderScheduler.setEnabled(true) } answers { reminderPrefs.enabled = true }

        vm.onNotificationPermissionResult(true)

        coVerify { reminderScheduler.setEnabled(true) }
        assertEquals(ReminderRowState.On, vm.reminderRow.value)
    }

    @Test
    fun `a denied permission result leaves reminders untouched`() = runTest {
        reminderPrefs.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)

        vm.onNotificationPermissionResult(false)

        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
        assertEquals(ReminderRowState.Off, vm.reminderRow.value)
    }

    @Test
    fun `onResumed recomputes the row from the gate`() = runTest {
        notificationGate.enabled = false
        reminderPrefs.enabled = true
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Granted)
        assertEquals(ReminderRowState.Blocked, vm.reminderRow.value)

        notificationGate.enabled = true
        vm.onResumed()
        assertEquals(ReminderRowState.On, vm.reminderRow.value)

        notificationGate.enabled = false
        vm.onResumed()
        assertEquals(ReminderRowState.Blocked, vm.reminderRow.value)
    }

    // -- Got This --

    @Test
    fun `the hints switch writes its pref and re-emits`() = runTest {
        val vm = viewModel()
        assertTrue(vm.gotThisHints.value)

        vm.onGotThisHintsSwitch(false)

        assertTrue(!gotThisPrefs.hintsEnabled)
        assertTrue(!vm.gotThisHints.value)
        vm.onGotThisHintsSwitch(true)
        assertTrue(gotThisPrefs.hintsEnabled)
        assertTrue(vm.gotThisHints.value)
    }

    @Test
    fun `the hints switch starts from the stored pref`() = runTest {
        gotThisPrefs.hintsEnabled = false

        assertTrue(!viewModel().gotThisHints.value)
    }

    @Test
    fun `running-low is On with the pref and notifications on`() = runTest {
        val vm = viewModel()

        assertEquals(ReminderRowState.On, vm.gotThisRunningLow.value)
    }

    @Test
    fun `running-low is Off when the pref is off whatever the gate says`() = runTest {
        gotThisPrefs.runningLowEnabled = false
        notificationGate.enabled = false

        assertEquals(ReminderRowState.Off, viewModel().gotThisRunningLow.value)
    }

    @Test
    fun `running-low is Blocked with the pref on and notifications off`() = runTest {
        notificationGate.enabled = false

        assertEquals(ReminderRowState.Blocked, viewModel().gotThisRunningLow.value)
    }

    @Test
    fun `running-low is Blocked when the permission was permanently refused`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()

        vm.updatePermissionState(PermissionState.PermanentlyRefused)

        assertEquals(ReminderRowState.Blocked, vm.gotThisRunningLow.value)
    }

    @Test
    fun `running-low is Blocked when Requestable and notifications are off`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()

        vm.updatePermissionState(PermissionState.Requestable)

        assertEquals(ReminderRowState.Blocked, vm.gotThisRunningLow.value)
    }

    @Test
    fun `onResumed recomputes running-low from the gate`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()
        assertEquals(ReminderRowState.Blocked, vm.gotThisRunningLow.value)

        notificationGate.enabled = true
        vm.onResumed()

        assertEquals(ReminderRowState.On, vm.gotThisRunningLow.value)
    }

    @Test
    fun `switching running-low off writes the pref and shows Off`() = runTest {
        val vm = viewModel()

        vm.onGotThisRunningLowSwitch(false)

        assertTrue(!gotThisPrefs.runningLowEnabled)
        assertEquals(ReminderRowState.Off, vm.gotThisRunningLow.value)
        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
    }

    @Test
    fun `switching running-low on with notifications on writes the pref and shows On`() = runTest {
        gotThisPrefs.runningLowEnabled = false
        val vm = viewModel()

        vm.onGotThisRunningLowSwitch(true)

        assertTrue(gotThisPrefs.runningLowEnabled)
        assertEquals(ReminderRowState.On, vm.gotThisRunningLow.value)
        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
        verify(exactly = 0) { reminderScheduler.onPermissionRequested() }
    }

    @Test
    fun `switching running-low on while blocked opens system settings`() = runTest {
        gotThisPrefs.runningLowEnabled = false
        notificationGate.enabled = false
        val vm = viewModel()

        vm.events.test {
            vm.onGotThisRunningLowSwitch(true)

            assertEquals(SettingsEvent.OpenNotificationSettings, awaitItem())
        }
        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
        verify(exactly = 0) { reminderScheduler.onPermissionRequested() }
    }

    @Test
    fun `switching running-low on when Requestable records the request and asks`() = runTest {
        gotThisPrefs.runningLowEnabled = false
        notificationGate.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)

        vm.events.test {
            vm.onGotThisRunningLowSwitch(true)

            assertEquals(SettingsEvent.RequestNotificationPermission, awaitItem())
        }
        verify { reminderScheduler.onPermissionRequested() }
        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
    }

    // The system dialog was asked for by the running-low switch, so a grant must
    // not turn the shopping reminders on, and a denial must not snooze anything.
    @Test
    fun `a granted result for the running-low request leaves shopping reminders alone`() = runTest {
        gotThisPrefs.runningLowEnabled = false
        reminderPrefs.enabled = false
        notificationGate.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)
        vm.events.test {
            vm.onGotThisRunningLowSwitch(true)
            awaitItem()
        }

        notificationGate.enabled = true
        vm.onNotificationPermissionResult(true)

        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
        coVerify(exactly = 0) { reminderScheduler.onPermissionGranted(any()) }
        assertEquals(ReminderRowState.On, vm.gotThisRunningLow.value)
        assertEquals(ReminderRowState.Off, vm.reminderRow.value)
    }

    @Test
    fun `a denied result for the running-low request touches no reminder state`() = runTest {
        gotThisPrefs.runningLowEnabled = false
        notificationGate.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)
        vm.events.test {
            vm.onGotThisRunningLowSwitch(true)
            awaitItem()
        }

        vm.onNotificationPermissionResult(false)

        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
        verify(exactly = 0) { reminderScheduler.onPermissionDenied() }
        verify(exactly = 0) { reminderScheduler.snoozePrompt() }
        coVerify(exactly = 0) { reminderScheduler.onPermissionGranted(any()) }
        assertTrue(gotThisPrefs.runningLowEnabled)
        assertEquals(ReminderRowState.Blocked, vm.gotThisRunningLow.value)
    }

    @Test
    fun `a later shopping-reminder result still enables reminders after a running-low request`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)
        vm.events.test {
            vm.onGotThisRunningLowSwitch(true)
            awaitItem()
        }
        vm.onNotificationPermissionResult(false)

        vm.onNotificationPermissionResult(true)

        coVerify { reminderScheduler.setEnabled(true) }
    }

    @Test
    fun `switching running-low off from Blocked writes the pref false`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()
        assertEquals(ReminderRowState.Blocked, vm.gotThisRunningLow.value)

        vm.onGotThisRunningLowSwitch(false)

        assertTrue(!gotThisPrefs.runningLowEnabled)
        assertEquals(ReminderRowState.Off, vm.gotThisRunningLow.value)
    }

    @Test
    fun `tapping the row while Blocked and Requestable asks for permission and keeps the pref`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)

        vm.events.test {
            vm.onGotThisRunningLowRowTap()

            assertEquals(SettingsEvent.RequestNotificationPermission, awaitItem())
        }
        verify { reminderScheduler.onPermissionRequested() }
        assertTrue(gotThisPrefs.runningLowEnabled)
        assertEquals(ReminderRowState.Blocked, vm.gotThisRunningLow.value)
    }

    @Test
    fun `tapping the row while Blocked and permanently refused opens system settings`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.PermanentlyRefused)

        vm.events.test {
            vm.onGotThisRunningLowRowTap()

            assertEquals(SettingsEvent.OpenNotificationSettings, awaitItem())
        }
        verify(exactly = 0) { reminderScheduler.onPermissionRequested() }
        assertTrue(gotThisPrefs.runningLowEnabled)
    }

    @Test
    fun `tapping the row toggles the pref when it is not Blocked`() = runTest {
        val vm = viewModel()

        vm.onGotThisRunningLowRowTap()
        assertEquals(ReminderRowState.Off, vm.gotThisRunningLow.value)
        assertTrue(!gotThisPrefs.runningLowEnabled)

        vm.onGotThisRunningLowRowTap()
        assertEquals(ReminderRowState.On, vm.gotThisRunningLow.value)
        assertTrue(gotThisPrefs.runningLowEnabled)
    }

    @Test
    fun `Blocked becomes On after a grant`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)
        vm.events.test {
            vm.onGotThisRunningLowRowTap()
            awaitItem()
        }

        notificationGate.enabled = true
        vm.onNotificationPermissionResult(true)

        assertEquals(ReminderRowState.On, vm.gotThisRunningLow.value)
        coVerify(exactly = 0) { reminderScheduler.setEnabled(any()) }
    }

    @Test
    fun `Blocked becomes On after returning from system settings`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.PermanentlyRefused)
        assertEquals(ReminderRowState.Blocked, vm.gotThisRunningLow.value)

        notificationGate.enabled = true
        vm.onResumed()

        assertEquals(ReminderRowState.On, vm.gotThisRunningLow.value)
    }

    // The event can be lost (nobody collecting), leaving the flag set with no
    // dialog ever shown; the next shopping-reminders answer must still count.
    @Test
    fun `a stale Got This request does not swallow a later shopping-reminders result`() = runTest {
        notificationGate.enabled = false
        val vm = viewModel()
        vm.updatePermissionState(PermissionState.Requestable)
        vm.onGotThisRunningLowRowTap()

        vm.onReminderSwitch(true)
        vm.onNotificationPermissionResult(true)

        coVerify { reminderScheduler.setEnabled(true) }
    }

    @Test
    fun `clearing purchase history clears the owner's history once`() = runTest {
        every { authStateManager.dataOwnerId() } returns "owner-1"
        val vm = viewModel()

        vm.onClearPurchaseHistory()

        coVerify(exactly = 1) { gotThisRepository.clearHistory("owner-1") }
    }

    @Test
    fun `clearing purchase history signed out passes a null owner`() = runTest {
        every { authStateManager.dataOwnerId() } returns null
        val vm = viewModel()

        vm.onClearPurchaseHistory()

        coVerify(exactly = 1) { gotThisRepository.clearHistory(null) }
    }

    @Test
    fun `permissionRequested delegates to the scheduler`() = runTest {
        every { reminderScheduler.permissionRequested() } returns true

        assertTrue(viewModel().permissionRequested())
    }
}

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val viewModel = mockk<SettingsViewModel>(relaxed = true)

    // Rendering only. Every row opens a dialog whose ViewModel defaults to
    // hiltViewModel(), which cannot resolve under a plain Compose rule — so the
    // row -> dialog wiring is left to the e2e suite, and the dialogs themselves
    // are tested directly in PinDialogsTest with a stubbed ViewModel.
    private fun render(
        pinExists: Boolean,
        lockedCount: Int = 0,
        analyticsEnabled: Boolean = true,
        crashReportingEnabled: Boolean = true,
        runningLow: ReminderRowState = ReminderRowState.On,
    ) {
        every { viewModel.pinExists } returns MutableStateFlow(pinExists)
        every { viewModel.lockedCount } returns MutableStateFlow(lockedCount)
        // A relaxed mock hands back a StateFlow whose value is a bare Object,
        // which blows up on the Boolean cast the moment the screen collects it.
        every { viewModel.analyticsEnabled } returns MutableStateFlow(analyticsEnabled)
        every { viewModel.crashReportingEnabled } returns MutableStateFlow(crashReportingEnabled)
        every { viewModel.reminderRow } returns MutableStateFlow(ReminderRowState.Off)
        every { viewModel.gotThisHints } returns MutableStateFlow(true)
        every { viewModel.gotThisRunningLow } returns MutableStateFlow(runningLow)
        every { viewModel.events } returns MutableSharedFlow()
        compose.setContent { SettingsScreen(viewModel = viewModel) }
    }

    @Test
    fun `with no pin, only the set-up row is offered`() {
        render(pinExists = false)

        compose.onNodeWithText("Set up a PIN").assertIsDisplayed()
        compose.onNodeWithText("Change PIN").assertDoesNotExist()
        compose.onNodeWithText("Remove PIN").assertDoesNotExist()
    }

    @Test
    fun `the set-up row explains what a PIN does`() {
        render(pinExists = false)

        compose.onNodeWithText(
            "Lock individual lists with a 4-digit PIN, stored only on this device",
        ).assertIsDisplayed()
    }

    @Test
    fun `with a pin, the management rows replace set-up`() {
        render(pinExists = true)

        compose.onNodeWithText("Set up a PIN").assertDoesNotExist()
        compose.onNodeWithText("Change PIN").assertIsDisplayed()
        compose.onNodeWithText("Remove PIN").assertIsDisplayed()
        compose.onNodeWithText("Regenerate recovery code").assertIsDisplayed()
        compose.onNodeWithText("Forgot your PIN?").assertIsDisplayed()
    }

    @Test
    fun `the device-only caveat is always shown`() {
        render(pinExists = true)

        compose.onNodeWithText(
            "Your PIN is stored on this device only. It is not tied to your account and won't sync.",
        ).assertExists()
    }

    // Play's User Data policy wants the privacy policy reachable inside the app,
    // not only from the store listing.
    @Test
    fun `the privacy policy row opens the published policy`() {
        render(pinExists = false)

        compose.onNodeWithText("Privacy policy").performSemanticsAction(SemanticsActions.OnClick)

        val started = org.robolectric.Shadows.shadowOf(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>(),
        ).nextStartedActivity
        assertEquals("https://babegetthis.com/privacy-policy.html", started.dataString)
    }

    @Test
    fun `opening settings refreshes the locked count`() {
        render(pinExists = true, lockedCount = 2)

        io.mockk.verify { viewModel.refreshLockedCount() }
    }

    // Granted with the OS gate closed is the real Blocked case (the user turned
    // notifications off in system settings). Without the grant, API 33+ reads
    // Requestable and the row is askable, not Blocked.
    @Test
    fun `the reminders row shows the blocked subtitle when blocked`() {
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val pinExists = MutableStateFlow(false)
        val pinRepository = mockk<PinRepository>(relaxed = true) {
            every { this@mockk.pinExists } returns pinExists
        }
        val consent = mockk<TelemetryConsent>(relaxed = true)
        val vm = SettingsViewModel(
            pinRepository,
            mockk(relaxed = true),
            consent,
            CoroutineScope(UnconfinedTestDispatcher()),
            mockk(relaxed = true),
            FakeReminderPrefs(),
            NotificationGate { false },
            FakeGotThisPrefs(),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        compose.setContent { SettingsScreen(viewModel = vm) }

        compose.onNodeWithText("Shopping reminders").assertIsDisplayed()
        compose.onNodeWithText("Allow notifications in system settings to turn this on").assertIsDisplayed()
        // The reminders switch is the first toggle on the screen; Blocked must never read as on.
        compose.onAllNodes(isToggleable())[0].assertIsOff()
    }

    // The permission state and the OS gate can change while the user is in system settings, so
    // resuming must re-read the gate. Fails if the ON_RESUME observer is removed from the screen.
    @Test
    fun `the reminders row recovers from Blocked when the screen resumes`() {
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val gate = FakeNotificationGate(enabled = false)
        val pinRepository = mockk<PinRepository>(relaxed = true) {
            every { this@mockk.pinExists } returns MutableStateFlow(false)
        }
        val vm = SettingsViewModel(
            pinRepository,
            mockk(relaxed = true),
            mockk<TelemetryConsent>(relaxed = true),
            CoroutineScope(UnconfinedTestDispatcher()),
            mockk(relaxed = true),
            FakeReminderPrefs(),
            gate,
            FakeGotThisPrefs(),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
        compose.setContent { SettingsScreen(viewModel = vm) }
        compose.onNodeWithText("Allow notifications in system settings to turn this on").assertIsDisplayed()

        gate.enabled = true
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText("A nudge when you leave items unticked after a shop").assertIsDisplayed()
        compose.onAllNodes(isToggleable())[0].assertIsOn()
    }

    // API 34, never asked: areNotificationsEnabled() is false only because the
    // grant is missing. The row must stay askable so the direct request can run.
    @Test
    fun `a never-asked user on API 33+ sees the plain subtitle, not Blocked`() {
        val pinRepository = mockk<PinRepository>(relaxed = true) {
            every { this@mockk.pinExists } returns MutableStateFlow(false)
        }
        val vm = SettingsViewModel(
            pinRepository,
            mockk(relaxed = true),
            mockk<TelemetryConsent>(relaxed = true),
            CoroutineScope(UnconfinedTestDispatcher()),
            mockk(relaxed = true),
            FakeReminderPrefs(),
            NotificationGate { false },
            FakeGotThisPrefs(),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        compose.setContent { SettingsScreen(viewModel = vm) }

        compose.onNodeWithText("A nudge when you leave items unticked after a shop").assertIsDisplayed()
        compose.onNodeWithText("Allow notifications in system settings to turn this on").assertDoesNotExist()
        assertEquals(ReminderRowState.Off, vm.reminderRow.value)
    }

    @Test
    fun `the Got This section shows its rows`() {
        render(pinExists = false)

        compose.onNodeWithText("Got This").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bought-recently hints").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Running-low reminders").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Suggest things you usually buy, with one heads-up a week",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Clear purchase history").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the running-low row shows the blocked subtitle when blocked`() {
        render(pinExists = false, runningLow = ReminderRowState.Blocked)

        compose.onNodeWithText("Notifications are off for this app").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Suggest things you usually buy, with one heads-up a week").assertDoesNotExist()
    }

    @Test
    fun `tapping the Got This switches calls the view model`() {
        render(pinExists = false)

        compose.onNodeWithText("Bought-recently hints").performScrollTo().performClick()
        compose.onNodeWithText("Running-low reminders").performScrollTo().performClick()

        verify { viewModel.onGotThisHintsSwitch(false) }
        verify { viewModel.onGotThisRunningLowRowTap() }
    }

    @Test
    fun `the Got This switches report the toggled value`() {
        render(pinExists = false, runningLow = ReminderRowState.Off)

        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        compose.onAllNodes(isToggleable())[2].performScrollTo().performClick()

        verify { viewModel.onGotThisHintsSwitch(false) }
        verify { viewModel.onGotThisRunningLowSwitch(true) }
    }

    @Test
    fun `the running-low switch is drawn on while Blocked and a row tap goes to the view model`() {
        render(pinExists = false, runningLow = ReminderRowState.Blocked)

        compose.onAllNodes(isToggleable())[2].performScrollTo().assertIsOn()
        compose.onNodeWithText("Running-low reminders").performScrollTo().performClick()

        verify { viewModel.onGotThisRunningLowRowTap() }
    }

    @Test
    fun `the clear row opens a confirmation with the exact message`() {
        render(pinExists = false)

        compose.onNodeWithText("Clear purchase history").performScrollTo().performClick()

        compose.onNodeWithText(
            "This deletes your purchase history on all your devices. Your lists stay as they are, " +
                "and people you share lists with keep their own history.",
        ).assertIsDisplayed()
        verify(exactly = 0) { viewModel.onClearPurchaseHistory() }
    }

    @Test
    fun `confirming the clear dialog clears the history and closes it`() {
        render(pinExists = false)
        compose.onNodeWithText("Clear purchase history").performScrollTo().performClick()

        compose.onNodeWithText("Clear").performClick()

        verify(exactly = 1) { viewModel.onClearPurchaseHistory() }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun `cancelling the clear dialog does not clear anything`() {
        render(pinExists = false)
        compose.onNodeWithText("Clear purchase history").performScrollTo().performClick()

        compose.onNodeWithText("Cancel").performClick()

        verify(exactly = 0) { viewModel.onClearPurchaseHistory() }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun `the reminders row shows the plain subtitle when not blocked`() {
        render(pinExists = false)

        compose.onNodeWithText("A nudge when you leave items unticked after a shop").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
class SettingsRowTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a row shows its title`() {
        compose.setContent { SettingsRow(title = "Change PIN", onClick = {}) }

        compose.onNodeWithText("Change PIN").assertIsDisplayed()
    }

    @Test
    fun `a subtitle is shown when supplied`() {
        compose.setContent {
            SettingsRow(title = "Set up a PIN", subtitle = "Lock individual lists", onClick = {})
        }

        compose.onNodeWithText("Lock individual lists").assertIsDisplayed()
    }

    @Test
    fun `no subtitle row is rendered when none is supplied`() {
        compose.setContent { SettingsRow(title = "Change PIN", onClick = {}) }

        compose.onNodeWithText("Lock individual lists").assertDoesNotExist()
    }

    @Test
    fun `tapping a row dispatches its callback`() {
        var clicked = false
        compose.setContent { SettingsRow(title = "Change PIN", onClick = { clicked = true }) }

        compose.onNodeWithText("Change PIN").performSemanticsAction(SemanticsActions.OnClick)

        assertTrue(clicked)
    }
}
