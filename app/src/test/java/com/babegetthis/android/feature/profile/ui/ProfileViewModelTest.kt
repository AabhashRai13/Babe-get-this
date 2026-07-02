package com.babegetthis.android.feature.profile.ui

import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.TokenManager
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import io.mockk.coEvery
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
class ProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ProfileViewModel(authRepository, tokenManager)

    @Test
    fun `successful account deletion emits a confirmation toast`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.Success(Unit)

        val viewModel = buildViewModel()

        viewModel.toastEvent.test {
            viewModel.deleteAccount()
            assertEquals("Your account has been deleted", awaitItem())
        }
        assertFalse(viewModel.uiState.value.isDeletingAccount)
    }

    @Test
    fun `failed account deletion surfaces the error and stops the spinner`() = runTest {
        coEvery { authRepository.deleteAccount() } returns
            Result.Error(AppError.NetworkError())

        val viewModel = buildViewModel()

        viewModel.toastEvent.test {
            viewModel.deleteAccount()
            assertEquals(AppError.NetworkError().message, awaitItem())
        }
        assertFalse(viewModel.uiState.value.isDeletingAccount)
    }
}
