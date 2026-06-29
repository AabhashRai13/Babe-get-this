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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

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

    private fun buildViewModel() = LoginViewModel(authRepository)

    // Helper: fill the form with valid values so the button would be enabled.
    private fun LoginViewModel.fillValid() {
        onEmailChange("a@b.com")
        onPasswordChange("secret1")
    }

    @Test
    fun `pristine form is invalid and shows no errors`() {
        val viewModel = buildViewModel()

        assertFalse(viewModel.uiState.value.isFormValid)
        assertNull(viewModel.uiState.value.emailError)
    }

    @Test
    fun `bad email sets an inline error and keeps the form invalid`() {
        val viewModel = buildViewModel()

        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("secret1")

        assertNotNull(viewModel.uiState.value.emailError)
        assertFalse(viewModel.uiState.value.isFormValid)
    }

    @Test
    fun `valid email and non-empty password make the form valid`() {
        val viewModel = buildViewModel()

        viewModel.fillValid()

        assertNull(viewModel.uiState.value.emailError)
        assertTrue(viewModel.uiState.value.isFormValid)
    }

    @Test
    fun `login on an invalid form does not call the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEmailChange("nope") // invalid, password still blank
        viewModel.login()

        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `successful login emits loginSuccess and stops loading`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
            Result.Success(User(id = "u1", email = "a@b.com", name = "Ann"))

        val viewModel = buildViewModel()
        viewModel.fillValid()

        viewModel.loginSuccess.test {
            viewModel.login()
            awaitItem()
        }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `failed login surfaces the error message and does not signal success`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
            Result.Error(AppError.AuthError("Invalid email or password."))

        val viewModel = buildViewModel()
        viewModel.fillValid()

        viewModel.loginSuccess.test {
            viewModel.login()
            expectNoEvents()
        }
        assertEquals(
            "Invalid email or password.",
            viewModel.uiState.value.errorMessage,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `clearError wipes the current error message`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
            Result.Error(AppError.AuthError("Invalid email or password."))

        val viewModel = buildViewModel()
        viewModel.fillValid()
        viewModel.login()
        assertEquals("Invalid email or password.", viewModel.uiState.value.errorMessage)

        viewModel.clearError()

        assertEquals(null, viewModel.uiState.value.errorMessage)
    }
}
