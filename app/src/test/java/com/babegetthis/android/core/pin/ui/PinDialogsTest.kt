package com.babegetthis.android.core.pin.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.babegetthis.android.core.pin.data.PinResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Every PIN dialog takes its ViewModel as a parameter, so each is driven here
// with a stubbed one. Buttons are actioned through the semantics tree — these
// are AlertDialogs whose button row can sit below the fold on Robolectric's
// default display.
@RunWith(RobolectricTestRunner::class)
class PinDialogsTest {

    @get:Rule val compose = createComposeRule()

    private val vm = mockk<PinPromptViewModel>(relaxed = true)

    private var verified = false
    private var dismissed = false
    private var completed = false

    private fun click(label: String) =
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)

    private fun typePin(pin: String) =
        compose.onNodeWithText("PIN").performTextInput(pin)

    // --- PinPromptDialog ---

    private fun prompt(purpose: PinPromptPurpose = PinPromptPurpose.Unlock) = compose.setContent {
        PinPromptDialog(
            purpose = purpose,
            onVerified = { verified = true },
            onDismiss = { dismissed = true },
            vm = vm,
        )
    }

    @Test
    fun `the unlock prompt names its purpose`() {
        prompt(PinPromptPurpose.Unlock)

        compose.onNodeWithText("Unlock list").assertExists()
    }

    @Test
    fun `the delete prompt names its purpose`() {
        prompt(PinPromptPurpose.Delete)

        compose.onNodeWithText("Delete locked list").assertExists()
    }

    @Test
    fun `confirm is disabled until four digits are entered`() {
        prompt()

        compose.onNodeWithText("Unlock").assertIsNotEnabled()

        typePin("123")
        compose.onNodeWithText("Unlock").assertIsNotEnabled()

        typePin("4")
        compose.onNodeWithText("Unlock").assertIsEnabled()
    }

    @Test
    fun `a correct pin reports verified`() {
        coEvery { vm.verify("1234") } returns PinResult.Success
        prompt()
        typePin("1234")

        click("Unlock")

        assertTrue(verified)
    }

    @Test
    fun `a wrong pin shows how many attempts remain`() {
        coEvery { vm.verify(any()) } returns PinResult.Wrong(attemptsRemaining = 3)
        prompt()
        typePin("0000")

        click("Unlock")

        compose.onNodeWithText("Wrong PIN. 3 attempts left.").assertExists()
        assertTrue(!verified)
    }

    // The field is cleared after a wrong entry, so the button falls back to
    // disabled rather than letting the user re-submit the same wrong PIN.
    @Test
    fun `a wrong pin clears the field`() {
        coEvery { vm.verify(any()) } returns PinResult.Wrong(attemptsRemaining = 3)
        prompt()
        typePin("0000")

        click("Unlock")

        compose.onNodeWithText("Unlock").assertIsNotEnabled()
    }

    @Test
    fun `a lockout shows the remaining time and blocks further entry`() {
        coEvery { vm.verify(any()) } returns PinResult.LockedOut(remainingMs = 30_000)
        prompt()
        typePin("0000")

        click("Unlock")

        compose.onNodeWithText("Too many attempts", substring = true).assertExists()
        compose.onNodeWithText("Unlock").assertIsNotEnabled()
    }

    @Test
    fun `cancel dismisses without verifying`() {
        prompt()

        click("Cancel")

        assertTrue(dismissed)
        coVerify(exactly = 0) { vm.verify(any()) }
    }

    // --- PinSetupDialog ---

    private fun setup() = compose.setContent {
        PinSetupDialog(
            onComplete = { completed = true },
            onDismiss = { dismissed = true },
            vm = vm,
        )
    }

    @Test
    fun `setup asks for a pin first`() {
        setup()

        compose.onNodeWithText("Create a PIN").assertExists()
    }

    @Test
    fun `setup moves to confirmation once four digits are entered`() {
        setup()
        typePin("1234")

        click("Continue")

        compose.onNodeWithText("Confirm your PIN").assertExists()
    }

    @Test
    fun `setup rejects a mismatched confirmation`() {
        setup()
        typePin("1234")
        click("Continue")
        typePin("5678")

        click("Confirm")

        compose.onNodeWithText("PINs don't match. Try again.").assertExists()
        coVerify(exactly = 0) { vm.setupPin(any()) }
    }

    @Test
    fun `setup stores the pin when both entries agree`() {
        coEvery { vm.setupPin("1234") } returns "ABCDEFGHJK"
        setup()
        typePin("1234")
        click("Continue")
        typePin("1234")

        click("Confirm")

        coVerify { vm.setupPin("1234") }
    }

    // The recovery code is shown once and must be acknowledged — this is the
    // user's only fallback if they forget the PIN.
    @Test
    fun `setup shows the recovery code before completing`() {
        coEvery { vm.setupPin(any()) } returns "ABCDEFGHJK"
        setup()
        typePin("1234")
        click("Continue")
        typePin("1234")
        click("Confirm")

        compose.onNodeWithText("ABCDEFGHJK", substring = true).assertExists()
        assertTrue("must not complete before acknowledgement", !completed)
    }

    @Test
    fun `cancelling setup stores nothing`() {
        setup()

        click("Cancel")

        assertTrue(dismissed)
        coVerify(exactly = 0) { vm.setupPin(any()) }
    }

    // --- ChangePinDialog ---

    @Test
    fun `change asks for the current pin first`() {
        compose.setContent {
            ChangePinDialog(onComplete = { completed = true }, onDismiss = {}, vm = vm)
        }

        compose.onNodeWithText("PIN").assertExists()
    }

    @Test
    fun `change rejects a wrong current pin`() {
        coEvery { vm.verify(any()) } returns PinResult.Wrong(attemptsRemaining = 2)
        compose.setContent {
            ChangePinDialog(onComplete = { completed = true }, onDismiss = {}, vm = vm)
        }
        typePin("0000")

        click("Continue")

        coVerify(exactly = 0) { vm.changePin(any(), any()) }
        assertTrue(!completed)
    }

    // --- RecoveryResetDialog ---

    @Test
    fun `recovery reset asks for the code first`() {
        compose.setContent {
            RecoveryResetDialog(onComplete = { completed = true }, onDismiss = {}, vm = vm)
        }

        compose.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun `a wrong recovery code does not reset anything`() {
        coEvery { vm.verifyRecovery(any()) } returns PinResult.Wrong(attemptsRemaining = 4)
        compose.setContent {
            RecoveryResetDialog(onComplete = { completed = true }, onDismiss = {}, vm = vm)
        }

        coVerify(exactly = 0) { vm.resetWithRecovery(any(), any()) }
        assertTrue(!completed)
    }
}
