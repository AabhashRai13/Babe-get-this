package com.babegetthis.android.feature.settings.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.pin.data.PinRepository
import com.babegetthis.android.core.ui.components.SettingsRow
import com.babegetthis.android.feature.shoppinglist.data.repository.ShoppingListRepository
import com.babegetthis.android.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun viewModel(): SettingsViewModel {
        every { pinRepository.pinExists } returns pinExists
        return SettingsViewModel(
            pinRepository,
            listRepository,
            CoroutineScope(UnconfinedTestDispatcher()),
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
            com.babegetthis.android.core.error.AppError.DatabaseError()
        )
        val vm = viewModel()

        // A failed unlock is cosmetic now, so it must not blow up.
        vm.onPinRemoved()

        coVerify { listRepository.unlockAll() }
    }
}

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule val compose = createComposeRule()

    private val viewModel = mockk<SettingsViewModel>(relaxed = true)

    // Rendering only. Every row opens a dialog whose ViewModel defaults to
    // hiltViewModel(), which cannot resolve under a plain Compose rule — so the
    // row -> dialog wiring is left to the e2e suite, and the dialogs themselves
    // are tested directly in PinDialogsTest with a stubbed ViewModel.
    private fun render(pinExists: Boolean, lockedCount: Int = 0) {
        every { viewModel.pinExists } returns MutableStateFlow(pinExists)
        every { viewModel.lockedCount } returns MutableStateFlow(lockedCount)
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
            "Lock individual lists with a 4-digit PIN, stored only on this device"
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

    @Test
    fun `opening settings refreshes the locked count`() {
        render(pinExists = true, lockedCount = 2)

        io.mockk.verify { viewModel.refreshLockedCount() }
    }
}

@RunWith(RobolectricTestRunner::class)
class SettingsRowTest {

    @get:Rule val compose = createComposeRule()

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
