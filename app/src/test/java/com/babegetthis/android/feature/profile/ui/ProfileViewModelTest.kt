package com.babegetthis.android.feature.profile.ui

import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var authStateManager: AuthStateManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authStateManager = mockk(relaxed = true)
        every { authStateManager.userName } returns MutableStateFlow(null)
        every { authStateManager.userEmail } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ProfileViewModel(authRepository, authStateManager)

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
    fun `switching accounts refreshes the displayed name and email`() = runTest {
        val userName = MutableStateFlow<String?>("Alice")
        val userEmail = MutableStateFlow<String?>("alice@x.com")
        every { authStateManager.userName } returns userName
        every { authStateManager.userEmail } returns userEmail

        val viewModel = buildViewModel()
        assertEquals("Alice", viewModel.uiState.value.userName)
        assertEquals("alice@x.com", viewModel.uiState.value.userEmail)

        // Log out, then log in as a different account.
        userName.value = null
        userEmail.value = null
        userName.value = "Bob"
        userEmail.value = "bob@x.com"

        assertEquals("Bob", viewModel.uiState.value.userName)
        assertEquals("bob@x.com", viewModel.uiState.value.userEmail)
        assertEquals("Bob", viewModel.uiState.value.editedName)
    }

    @Test
    fun `email refresh is reflected even when two accounts share a display name`() = runTest {
        val userName = MutableStateFlow<String?>("Dev User")
        val userEmail = MutableStateFlow<String?>("first@x.com")
        every { authStateManager.userName } returns userName
        every { authStateManager.userEmail } returns userEmail

        val viewModel = buildViewModel()
        assertEquals("first@x.com", viewModel.uiState.value.userEmail)

        // Same name on the next account — only the email changes.
        userEmail.value = "second@x.com"

        assertEquals("second@x.com", viewModel.uiState.value.userEmail)
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
