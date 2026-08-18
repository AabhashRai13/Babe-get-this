package com.babegetthis.android.core.auth.ui

import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.RegisterResult
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

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

    private fun buildViewModel() = RegisterViewModel(authRepository, mockk(relaxed = true))

    private val user = User(id = "u1", email = "a@b.com", name = "Ann")

    // Helper: fill the form with valid values so the button would be enabled.
    private fun RegisterViewModel.fillValid() {
        onNameChange("Ann")
        onEmailChange("a@b.com")
        onPasswordChange("secret1")
        onConfirmPasswordChange("secret1")
    }

    // -- Client-side validation (must not hit the repository) --

    @Test
    fun `pristine form is invalid and calling register does not hit the repository`() = runTest {
        val viewModel = buildViewModel()

        assertFalse(viewModel.uiState.value.isFormValid)
        viewModel.register()

        coVerify(exactly = 0) { authRepository.register(any(), any(), any()) }
    }

    @Test
    fun `mismatched passwords set a confirm error and keep the form invalid`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onNameChange("Ann")
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("secret1")
        viewModel.onConfirmPasswordChange("secret2")

        assertNotNull(viewModel.uiState.value.confirmPasswordError)
        assertFalse(viewModel.uiState.value.isFormValid)
        viewModel.register()
        coVerify(exactly = 0) { authRepository.register(any(), any(), any()) }
    }

    @Test
    fun `too-short password sets a password error and keeps the form invalid`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onNameChange("Ann")
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("123")
        viewModel.onConfirmPasswordChange("123")

        assertNotNull(viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.isFormValid)
        viewModel.register()
        coVerify(exactly = 0) { authRepository.register(any(), any(), any()) }
    }

    @Test
    fun `bad email sets an email error and keeps the form invalid`() {
        val viewModel = buildViewModel()

        viewModel.onEmailChange("not-an-email")

        assertNotNull(viewModel.uiState.value.emailError)
        assertFalse(viewModel.uiState.value.isFormValid)
    }

    @Test
    fun `fully valid form clears errors and is valid`() {
        val viewModel = buildViewModel()

        viewModel.fillValid()

        assertNull(viewModel.uiState.value.emailError)
        assertNull(viewModel.uiState.value.passwordError)
        assertNull(viewModel.uiState.value.confirmPasswordError)
        assertTrue(viewModel.uiState.value.isFormValid)
    }

    // -- RegisterResult branching --

    @Test
    fun `SignedIn result emits registerSuccess and stops loading`() = runTest {
        coEvery { authRepository.register(any(), any(), any()) } returns
            Result.Success(RegisterResult.SignedIn(user))

        val viewModel = buildViewModel()
        viewModel.fillValid()

        viewModel.registerSuccess.test {
            viewModel.register()
            awaitItem() // success signal fired
        }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `ConfirmationRequired result shows the confirm message and does not signal success`() =
        runTest {
            coEvery { authRepository.register(any(), any(), any()) } returns
                Result.Success(RegisterResult.ConfirmationRequired)

            val viewModel = buildViewModel()
            viewModel.fillValid()

            viewModel.registerSuccess.test {
                viewModel.register()
                expectNoEvents() // confirmation is NOT a success
            }
            assertEquals(
                "Check your email to confirm your account, then sign in.",
                viewModel.uiState.value.errorMessage,
            )
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `repository error surfaces the error message`() = runTest {
        coEvery { authRepository.register(any(), any(), any()) } returns
            Result.Error(AppError.AuthError("That email is already registered."))

        val viewModel = buildViewModel()
        viewModel.fillValid()

        viewModel.register()

        assertEquals(
            "That email is already registered.",
            viewModel.uiState.value.errorMessage,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `clearError wipes the current error message`() = runTest {
        coEvery { authRepository.register(any(), any(), any()) } returns
            Result.Error(AppError.AuthError("That email is already registered."))

        val viewModel = buildViewModel()
        viewModel.fillValid()
        viewModel.register()
        assertEquals("That email is already registered.", viewModel.uiState.value.errorMessage)

        viewModel.clearError()

        assertEquals(null, viewModel.uiState.value.errorMessage)
    }
}
