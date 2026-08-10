package com.babegetthis.android.feature.profile.ui

import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.auth.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var authStateManager: AuthStateManager
    private lateinit var syncEngine: com.babegetthis.android.core.sync.data.repository.SyncEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authStateManager = mockk(relaxed = true)
        syncEngine = mockk(relaxed = true)
        every { authStateManager.userName } returns MutableStateFlow(null)
        every { authStateManager.userEmail } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ProfileViewModel(authRepository, authStateManager, syncEngine)

    // The shared fixture above seeds a null name/email (logged out), which the
    // existing tests depend on. Name-editing tests need somebody signed in, so
    // they seed their own rather than changing that default.
    private fun buildSignedIn(name: String = "Aabhash") = ProfileViewModel(
        authRepository,
        mockk(relaxed = true) {
            every { userName } returns MutableStateFlow(name)
            every { userEmail } returns MutableStateFlow("a@b.c")
        },
        syncEngine,
    )

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

    // -- onNameChanged / hasNameChanged --

    @Test
    fun `onNameChanged updates only the edited name`() = runTest {
        val vm = buildSignedIn()

        vm.onNameChanged("Renamed")

        assertEquals("Renamed", vm.uiState.value.editedName)
        assertEquals("Aabhash", vm.uiState.value.userName)
    }

    @Test
    fun `hasNameChanged is false until the name actually differs`() = runTest {
        val vm = buildSignedIn()
        assertFalse(vm.uiState.value.hasNameChanged)

        vm.onNameChanged("Aabhash")
        assertFalse(vm.uiState.value.hasNameChanged)

        vm.onNameChanged("Renamed")
        assertTrue(vm.uiState.value.hasNameChanged)
    }

    @Test
    fun `a blank edited name is not a change`() = runTest {
        val vm = buildSignedIn()

        vm.onNameChanged("   ")

        assertFalse(vm.uiState.value.hasNameChanged)
    }

    // -- saveName --

    @Test
    fun `saveName rejects a blank name without calling the repository`() = runTest {
        val vm = buildSignedIn()
        vm.onNameChanged("   ")

        vm.toastEvent.test {
            vm.saveName()
            assertEquals("Name cannot be empty", awaitItem())
        }
        coVerify(exactly = 0) { authRepository.updateUserName(any()) }
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `saveName trims before sending`() = runTest {
        coEvery { authRepository.updateUserName(any()) } returns
            Result.Success(User("u1", "a@b.c", "Renamed"))
        val vm = buildSignedIn()
        vm.onNameChanged("  Renamed  ")

        vm.saveName()

        coVerify { authRepository.updateUserName("Renamed") }
    }

    @Test
    fun `a successful save adopts the returned name and confirms`() = runTest {
        coEvery { authRepository.updateUserName(any()) } returns
            Result.Success(User("u1", "a@b.c", "Renamed"))
        val vm = buildSignedIn()
        vm.onNameChanged("Renamed")

        vm.toastEvent.test {
            vm.saveName()
            assertEquals("Name updated", awaitItem())
        }

        val state = vm.uiState.value
        assertEquals("Renamed", state.userName)
        assertEquals("Renamed", state.editedName)
        assertFalse(state.isSaving)
        assertFalse("nothing left to save", state.hasNameChanged)
    }

    @Test
    fun `a failed save surfaces the error and keeps the edit`() = runTest {
        coEvery { authRepository.updateUserName(any()) } returns
            Result.Error(AppError.NetworkError())
        val vm = buildSignedIn()
        vm.onNameChanged("Renamed")

        vm.toastEvent.test {
            vm.saveName()
            assertEquals(AppError.NetworkError().message, awaitItem())
        }

        val state = vm.uiState.value
        assertEquals("Aabhash", state.userName)
        assertEquals("the user's typing must not be discarded", "Renamed", state.editedName)
        assertFalse(state.isSaving)
    }

    // Pinned rather than endorsed: saveName does not consult hasNameChanged, so
    // saving an untouched name still round-trips to the backend.
    @Test
    fun `saving an unchanged name still calls the repository`() = runTest {
        coEvery { authRepository.updateUserName(any()) } returns
            Result.Success(User("u1", "a@b.c", "Aabhash"))
        val vm = buildSignedIn()

        vm.saveName()

        coVerify { authRepository.updateUserName("Aabhash") }
    }

    // -- logout --

    @Test
    fun `logout clears the session and stops the spinner`() = runTest {
        coEvery { authRepository.logout() } returns Result.Success(Unit)
        val vm = buildViewModel()

        vm.logout()

        coVerify { authRepository.logout() }
        assertFalse(vm.uiState.value.isLoggingOut)
    }

    // logout() is written to always succeed locally, so this arm is unreachable
    // through SupabaseAuthRepository. Covered anyway because the ViewModel takes
    // the AuthRepository interface, and the dev-flavour implementation is free to
    // fail — a Low finding in findings.md notes the branch is otherwise dead.
    @Test
    fun `a failing logout surfaces the error`() = runTest {
        coEvery { authRepository.logout() } returns Result.Error(AppError.UnknownError())
        val vm = buildViewModel()

        vm.toastEvent.test {
            vm.logout()
            assertEquals(AppError.UnknownError().message, awaitItem())
        }
        assertFalse(vm.uiState.value.isLoggingOut)
    }

    // --- shared-replica eviction (technical decision 004) ---

    @Test
    fun `logout evicts shared replicas BEFORE the session is destroyed`() = runTest {
        val vm = buildViewModel()

        vm.logout()

        coVerifyOrder {
            syncEngine.evictSharedReplicas() // final push still has a session
            authRepository.logout()
        }
    }

    @Test
    fun `deleteAccount evicts shared replicas BEFORE the account is destroyed`() = runTest {
        val vm = buildViewModel()

        vm.deleteAccount()

        coVerifyOrder {
            syncEngine.evictSharedReplicas()
            authRepository.deleteAccount()
        }
    }
}
