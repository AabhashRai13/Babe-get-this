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

    private fun buildViewModel() = RegisterViewModel(authRepository)

    private val user = User(id = "u1", email = "a@b.com", name = "Ann")

    // -- Client-side validation (must not hit the repository) --

    @Test
    fun `blank fields show an error and do not call the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.register(name = "", email = "", password = "", confirmPassword = "")

        assertEquals("Please fill in all fields.", viewModel.uiState.value.errorMessage)
        coVerify(exactly = 0) { authRepository.register(any(), any(), any()) }
    }

    @Test
    fun `mismatched passwords show an error and do not call the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.register(
            name = "Ann",
            email = "a@b.com",
            password = "secret1",
            confirmPassword = "secret2",
        )

        assertEquals("Passwords do not match.", viewModel.uiState.value.errorMessage)
        coVerify(exactly = 0) { authRepository.register(any(), any(), any()) }
    }

    @Test
    fun `too-short password shows an error and does not call the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.register(
            name = "Ann",
            email = "a@b.com",
            password = "123",
            confirmPassword = "123",
        )

        assertEquals(
            "Password must be at least 6 characters.",
            viewModel.uiState.value.errorMessage,
        )
        coVerify(exactly = 0) { authRepository.register(any(), any(), any()) }
    }

    // -- RegisterResult branching --

    @Test
    fun `SignedIn result emits registerSuccess and stops loading`() = runTest {
        coEvery { authRepository.register(any(), any(), any()) } returns
            Result.Success(RegisterResult.SignedIn(user))

        val viewModel = buildViewModel()

        viewModel.registerSuccess.test {
            viewModel.register("Ann", "a@b.com", "secret1", "secret1")
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

            viewModel.registerSuccess.test {
                viewModel.register("Ann", "a@b.com", "secret1", "secret1")
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

        viewModel.register("Ann", "a@b.com", "secret1", "secret1")

        assertEquals(
            "That email is already registered.",
            viewModel.uiState.value.errorMessage,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `clearError wipes the current error message`() = runTest {
        val viewModel = buildViewModel()
        viewModel.register("", "", "", "") // sets an error
        assertEquals("Please fill in all fields.", viewModel.uiState.value.errorMessage)

        viewModel.clearError()

        assertEquals(null, viewModel.uiState.value.errorMessage)
    }
}
