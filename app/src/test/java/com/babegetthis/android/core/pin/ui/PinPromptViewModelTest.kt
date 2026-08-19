package com.babegetthis.android.core.pin.ui

import com.babegetthis.android.core.pin.data.PinRepository
import com.babegetthis.android.core.pin.data.PinResult
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPromptViewModelTest {

    private val repository = mockk<PinRepository>(relaxed = true)
    private val viewModel = PinPromptViewModel(repository)

    // The ViewModel is a thin bridge whose whole job is to move the 120k-iteration
    // PBKDF2 work off the main thread. So what matters is that each call reaches
    // the repository and hands its result back untouched.

    @Test
    fun `verify delegates and returns the result unchanged`() = runTest {
        every { repository.verifyPin("1234") } returns PinResult.Success

        assertEquals(PinResult.Success, viewModel.verify("1234"))
        verify { repository.verifyPin("1234") }
    }

    @Test
    fun `verify passes a failure through as-is`() = runTest {
        every { repository.verifyPin(any()) } returns PinResult.Wrong(attemptsRemaining = 3)

        assertEquals(PinResult.Wrong(3), viewModel.verify("0000"))
    }

    @Test
    fun `verify passes a lockout through as-is`() = runTest {
        every { repository.verifyPin(any()) } returns PinResult.LockedOut(remainingMs = 30_000)

        assertEquals(PinResult.LockedOut(30_000), viewModel.verify("0000"))
    }

    @Test
    fun `setupPin delegates and returns the recovery code`() = runTest {
        every { repository.setupPin("1234") } returns "ABCDEFGHJK"

        assertEquals("ABCDEFGHJK", viewModel.setupPin("1234"))
        verify { repository.setupPin("1234") }
    }

    @Test
    fun `changePin delegates with both pins`() = runTest {
        every { repository.changePin("1234", "5678") } returns PinResult.Success

        assertEquals(PinResult.Success, viewModel.changePin("1234", "5678"))
        verify { repository.changePin("1234", "5678") }
    }

    @Test
    fun `removePin delegates`() = runTest {
        every { repository.removePin("1234") } returns PinResult.Success

        assertEquals(PinResult.Success, viewModel.removePin("1234"))
        verify { repository.removePin("1234") }
    }

    @Test
    fun `verifyRecovery delegates`() = runTest {
        every { repository.verifyRecoveryCode("ABC") } returns PinResult.Success

        assertEquals(PinResult.Success, viewModel.verifyRecovery("ABC"))
        verify { repository.verifyRecoveryCode("ABC") }
    }

    @Test
    fun `resetWithRecovery returns both halves of the pair`() = runTest {
        every { repository.resetPinWithRecoveryCode("ABC", "5678") } returns
            (PinResult.Success to "NEWCODE123")

        val (result, code) = viewModel.resetWithRecovery("ABC", "5678")

        assertEquals(PinResult.Success, result)
        assertEquals("NEWCODE123", code)
    }

    @Test
    fun `regenerateRecoveryCode delegates`() = runTest {
        every { repository.regenerateRecoveryCode() } returns "FRESHCODE1"

        assertEquals("FRESHCODE1", viewModel.regenerateRecoveryCode())
        verify { repository.regenerateRecoveryCode() }
    }
}

class IsValidPinTest {

    @Test
    fun `four digits are valid`() {
        assertTrue(isValidPin("1234"))
        assertTrue(isValidPin("0000"))
        assertTrue(isValidPin("9999"))
    }

    @Test
    fun `too short is rejected`() {
        assertFalse(isValidPin("123"))
        assertFalse(isValidPin("1"))
    }

    @Test
    fun `too long is rejected`() {
        assertFalse(isValidPin("12345"))
    }

    @Test
    fun `empty is rejected`() {
        assertFalse(isValidPin(""))
    }

    @Test
    fun `letters are rejected`() {
        assertFalse(isValidPin("12a4"))
        assertFalse(isValidPin("abcd"))
    }

    @Test
    fun `symbols and whitespace are rejected`() {
        assertFalse(isValidPin("12-4"))
        assertFalse(isValidPin("12 4"))
        assertFalse(isValidPin(" 123"))
    }

    // Char.isDigit() is true for non-ASCII digits, so "١٢٣٤" (Arabic-Indic) and
    // friends pass the length-and-digit check. Pinned as current behavior: they
    // are storable and verifiable, and the keypad only emits ASCII anyway, so
    // this is a curiosity rather than a hole. Worth knowing before anyone
    // switches to a custom keypad.
    @Test
    fun `non-ascii digits are accepted by the digit check`() {
        assertTrue(isValidPin("١٢٣٤"))
    }
}
