package com.babegetthis.android.feature.profile.ui

import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.onboarding.data.repository.WelcomeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private lateinit var welcome: WelcomeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authStateManager = mockk(relaxed = true)
        syncEngine = mockk(relaxed = true)
        welcome = mockk(relaxed = true)
        every { authStateManager.userName } returns MutableStateFlow(null)
        every { authStateManager.userEmail } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ProfileViewModel(authRepository, authStateManager, syncEngine, welcome)

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
        welcome,
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

    // --- account-list eviction (docs/technical-decisions/008) ---

    @Test
    fun `logout evicts the account's lists BEFORE the session is destroyed`() = runTest {
        val vm = buildViewModel()

        vm.logout()

        coVerifyOrder {
            syncEngine.push()
            syncEngine.evictAccountLists() // final push still has a session
            authRepository.logout()
        }
    }

    // Push while the session still exists, delete the account, and only then
    // take its lists off the phone: a deletion that fails (offline, server
    // error) must leave every list — and every unsynced edit — where it was.
    @Test
    fun `deleteAccount pushes first and evicts the lists only after the account is gone`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.Success(Unit)
        val vm = buildViewModel()

        vm.deleteAccount()

        coVerifyOrder {
            syncEngine.push()
            authRepository.deleteAccount()
            syncEngine.evictAccountLists()
        }
    }

    @Test
    fun `a failed account deletion keeps every list on the phone`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.Error(AppError.NetworkError())
        val vm = buildViewModel()

        vm.deleteAccount()

        coVerify(exactly = 0) { syncEngine.evictAccountLists() }
    }

    // --- signing out with changes that never reached the server ---
    //
    // Every list is an account list now, so an offline sign-out is no longer
    // the rare case decision 004 accepted silently losing edits for.

    @Test
    fun `signing out with everything synced also resets Welcome`() = runTest {
        coEvery { authRepository.logout() } returns Result.Success(Unit)
        val vm = buildViewModel()

        vm.logout()

        verify { welcome.reset() }
        assertFalse(vm.uiState.value.showUnsyncedSignOutWarning)
    }

    @Test
    fun `signing out with unsynced changes shows the warning and does nothing else`() = runTest {
        coEvery { syncEngine.hasUnsyncedChanges() } returns true
        val vm = buildViewModel()

        vm.logout()

        assertTrue(vm.uiState.value.showUnsyncedSignOutWarning)
        assertFalse(vm.uiState.value.isLoggingOut)
        coVerify(exactly = 0) { syncEngine.evictAccountLists() }
        coVerify(exactly = 0) { authRepository.logout() }
        verify(exactly = 0) { welcome.reset() }
    }

    @Test
    fun `sign out anyway completes the sign-out`() = runTest {
        coEvery { syncEngine.hasUnsyncedChanges() } returns true
        coEvery { authRepository.logout() } returns Result.Success(Unit)
        val vm = buildViewModel()
        vm.logout()

        vm.confirmSignOutAnyway()

        assertFalse(vm.uiState.value.showUnsyncedSignOutWarning)
        coVerifyOrder {
            syncEngine.evictAccountLists()
            authRepository.logout()
        }
        verify { welcome.reset() }
        assertFalse(vm.uiState.value.isLoggingOut)
    }

    @Test
    fun `dismissing the warning keeps the user signed in`() = runTest {
        coEvery { syncEngine.hasUnsyncedChanges() } returns true
        val vm = buildViewModel()
        vm.logout()

        vm.dismissUnsyncedSignOutWarning()

        assertFalse(vm.uiState.value.showUnsyncedSignOutWarning)
        coVerify(exactly = 0) { authRepository.logout() }
    }

    @Test
    fun `a failed sign-out leaves Welcome alone`() = runTest {
        coEvery { authRepository.logout() } returns Result.Error(AppError.UnknownError())
        val vm = buildViewModel()

        vm.logout()

        verify(exactly = 0) { welcome.reset() }
    }

    @Test
    fun `deleting the account resets Welcome`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.Success(Unit)
        val vm = buildViewModel()

        vm.deleteAccount()

        verify { welcome.reset() }
    }

    @Test
    fun `a failed deletion leaves Welcome alone`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.Error(AppError.UnknownError())
        val vm = buildViewModel()

        vm.deleteAccount()

        verify(exactly = 0) { welcome.reset() }
    }
}
