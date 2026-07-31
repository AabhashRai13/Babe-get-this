package com.babegetthis.android.core.auth.ui

import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ForgotPasswordViewModel(authRepository)

    @Test
    fun `sendCode with an invalid email does not call the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEmailChange("not-an-email")
        viewModel.sendCode()

        coVerify(exactly = 0) { authRepository.requestPasswordReset(any()) }
    }

    @Test
    fun `successful sendCode advances to the code step`() = runTest {
        coEvery { authRepository.requestPasswordReset(any()) } returns Result.Success(Unit)

        val viewModel = buildViewModel()
        viewModel.onEmailChange("a@b.com")
        viewModel.sendCode()

        assertTrue(viewModel.uiState.value.codeSent)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `failed sendCode surfaces the error and stays on the email step`() = runTest {
        coEvery { authRepository.requestPasswordReset(any()) } returns
            Result.Error(AppError.NetworkError())

        val viewModel = buildViewModel()
        viewModel.onEmailChange("a@b.com")
        viewModel.sendCode()

        assertFalse(viewModel.uiState.value.codeSent)
        assertEquals(
            AppError.NetworkError().message,
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun `code input keeps only digits and caps at eight`() {
        val viewModel = buildViewModel()

        viewModel.onCodeChange("12a3-4567 89 01")

        assertEquals("12345678", viewModel.uiState.value.code)
    }

    @Test
    fun `reset form needs a full code and a long-enough password`() {
        val viewModel = buildViewModel()

        viewModel.onCodeChange("1234567")
        viewModel.onNewPasswordChange("secret1")
        assertFalse(viewModel.uiState.value.isResetFormValid)

        viewModel.onCodeChange("12345678")
        viewModel.onNewPasswordChange("short")
        assertFalse(viewModel.uiState.value.isResetFormValid)

        viewModel.onNewPasswordChange("secret1")
        assertTrue(viewModel.uiState.value.isResetFormValid)
    }

    @Test
    fun `successful reset emits resetSuccess`() = runTest {
        coEvery { authRepository.resetPassword(any(), any(), any()) } returns
            Result.Success(User(id = "u1", email = "a@b.com", name = "Ann"))

        val viewModel = buildViewModel()
        viewModel.onEmailChange("a@b.com")
        viewModel.onCodeChange("12345678")
        viewModel.onNewPasswordChange("secret1")

        viewModel.resetSuccess.test {
            viewModel.resetPassword()
            awaitItem()
        }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `failed reset surfaces the error and does not signal success`() = runTest {
        coEvery { authRepository.resetPassword(any(), any(), any()) } returns
            Result.Error(AppError.AuthError("Invalid code. Check the email and try again."))

        val viewModel = buildViewModel()
        viewModel.onEmailChange("a@b.com")
        viewModel.onCodeChange("12345678")
        viewModel.onNewPasswordChange("secret1")

        viewModel.resetSuccess.test {
            viewModel.resetPassword()
            expectNoEvents()
        }
        assertEquals(
            "Invalid code. Check the email and try again.",
            viewModel.uiState.value.errorMessage,
        )
    }

    // clearError() had no caller in the whole suite — the last genuinely
    // uncovered line in core/auth. It is how the screen dismisses an error
    // snackbar, so leaving it unexercised meant nobody had checked it clears
    // the message without disturbing anything the user had typed.
    @Test
    fun `clearError removes the message and leaves the form intact`() = runTest {
        coEvery { authRepository.requestPasswordReset(any()) } returns
            Result.Error(AppError.NetworkError())
        val viewModel = ForgotPasswordViewModel(authRepository)
        viewModel.onEmailChange("a@b.c")
        viewModel.sendCode()
        assertEquals(AppError.NetworkError().message, viewModel.uiState.value.errorMessage)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("a@b.c", viewModel.uiState.value.email)
    }

    @Test
    fun `clearError is harmless when there is no error`() = runTest {
        val viewModel = ForgotPasswordViewModel(authRepository)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.errorMessage)
    }
}
