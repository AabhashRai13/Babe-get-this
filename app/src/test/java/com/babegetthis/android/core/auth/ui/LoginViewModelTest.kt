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

    @Test
    fun `blank fields show an error and do not call the repository`() = runTest {
        val viewModel = buildViewModel()

        viewModel.login(email = "", password = "")

        assertEquals("Please fill in all fields.", viewModel.uiState.value.errorMessage)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `successful login emits loginSuccess and stops loading`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns
            Result.Success(User(id = "u1", email = "a@b.com", name = "Ann"))

        val viewModel = buildViewModel()

        viewModel.loginSuccess.test {
            viewModel.login("a@b.com", "secret1")
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

        viewModel.loginSuccess.test {
            viewModel.login("a@b.com", "wrong")
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
        val viewModel = buildViewModel()
        viewModel.login("", "")
        assertEquals("Please fill in all fields.", viewModel.uiState.value.errorMessage)

        viewModel.clearError()

        assertEquals(null, viewModel.uiState.value.errorMessage)
    }
}
