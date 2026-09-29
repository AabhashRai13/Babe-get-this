package com.babegetthis.android.core.auth.ui

import android.content.Context
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import app.cash.turbine.test
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.GOOGLE_SIGN_IN_FAILED
import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.telemetry.CrashReporter
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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
    private lateinit var googleTokens: GoogleTokenRequester
    private lateinit var crashReporter: CrashReporter
    private val activity = mockk<Context>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        googleTokens = mockk()
        crashReporter = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = LoginViewModel(authRepository, mockk(relaxed = true), googleTokens, crashReporter)

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

    // --- Google ---

    private val token = GoogleSignInToken(idToken = "id-token", rawNonce = "raw-nonce")

    // Deliberately on a pristine form: Google sign-in must not depend on the
    // email/password fields being valid.
    @Test
    fun `google sign-in forwards the sheet's token and emits loginSuccess`() = runTest {
        coEvery { googleTokens.request(activity) } returns token
        coEvery { authRepository.signInWithGoogle("id-token", "raw-nonce") } returns
            Result.Success(User(id = "u1", email = "a@gmail.com", name = "Ann"))
        val viewModel = buildViewModel()

        viewModel.loginSuccess.test {
            viewModel.signInWithGoogle(activity)
            awaitItem()
        }
        coVerify(exactly = 1) { authRepository.signInWithGoogle("id-token", "raw-nonce") }
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `a rejected google token surfaces the error and does not signal success`() = runTest {
        coEvery { googleTokens.request(activity) } returns token
        coEvery { authRepository.signInWithGoogle(any(), any()) } returns
            Result.Error(AppError.AuthError(GOOGLE_SIGN_IN_FAILED))
        val viewModel = buildViewModel()

        viewModel.loginSuccess.test {
            viewModel.signInWithGoogle(activity)
            expectNoEvents()
        }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(GOOGLE_SIGN_IN_FAILED, viewModel.uiState.value.errorMessage)
    }

    // Closing the sheet is a choice, not a failure: no message, no repository
    // call, and the buttons come back.
    @Test
    fun `closing the google sheet is silent`() = runTest {
        coEvery { googleTokens.request(activity) } returns null
        val viewModel = buildViewModel()

        viewModel.loginSuccess.test {
            viewModel.signInWithGoogle(activity)
            expectNoEvents()
        }
        coVerify(exactly = 0) { authRepository.signInWithGoogle(any(), any()) }
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // A failing sheet (no Play services, OAuth client missing this build's
    // SHA-1) must reach Crashlytics — as UnknownError, the type the reporting
    // policy transmits — as well as the user.
    @Test
    fun `a failing google sheet is shown and reported`() {
        val failure = GetCredentialUnknownException("developer error")
        coEvery { googleTokens.request(activity) } throws failure
        val viewModel = buildViewModel()

        viewModel.signInWithGoogle(activity)

        assertEquals(GOOGLE_SIGN_IN_FAILED, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        verify { crashReporter.recordNonFatal(failure, AppError.UnknownError(GOOGLE_SIGN_IN_FAILED)) }
        coVerify(exactly = 0) { authRepository.signInWithGoogle(any(), any()) }
    }

    @Test
    fun `an unparseable google credential is shown and reported`() {
        val failure = GoogleIdTokenParsingException(IllegalArgumentException("bad bundle"))
        coEvery { googleTokens.request(activity) } throws failure
        val viewModel = buildViewModel()

        viewModel.signInWithGoogle(activity)

        assertEquals(GOOGLE_SIGN_IN_FAILED, viewModel.uiState.value.errorMessage)
        verify { crashReporter.recordNonFatal(failure, AppError.UnknownError(GOOGLE_SIGN_IN_FAILED)) }
    }

    // Offline the requester refuses to open the sheet; the user gets the
    // app's standard offline message, and nothing is reported (offline is not
    // a defect).
    @Test
    fun `google sign-in offline shows the network message`() {
        coEvery { googleTokens.request(activity) } throws GoogleSignInOfflineException()
        val viewModel = buildViewModel()

        viewModel.signInWithGoogle(activity)

        assertEquals(AppError.NetworkError().message, viewModel.uiState.value.errorMessage)
        verify(exactly = 0) { crashReporter.recordNonFatal(any(), any()) }
        coVerify(exactly = 0) { authRepository.signInWithGoogle(any(), any()) }
    }

    // No Google account on the device is the user's situation to fix, not a
    // defect: a specific message, and nothing sent to Crashlytics.
    @Test
    fun `no google account on the device asks the user to add one`() {
        coEvery { googleTokens.request(activity) } throws NoCredentialException()
        val viewModel = buildViewModel()

        viewModel.signInWithGoogle(activity)

        assertEquals(
            "Add a Google account to this device, then try again.",
            viewModel.uiState.value.errorMessage,
        )
        verify(exactly = 0) { crashReporter.recordNonFatal(any(), any()) }
    }

    // A double tap must not open a second sheet while the first is still up.
    @Test
    fun `a second google tap while the sheet is open is ignored`() {
        val sheet = CompletableDeferred<GoogleSignInToken?>()
        coEvery { googleTokens.request(activity) } coAnswers { sheet.await() }
        val viewModel = buildViewModel()

        viewModel.signInWithGoogle(activity)
        viewModel.signInWithGoogle(activity)

        coVerify(exactly = 1) { googleTokens.request(activity) }
        assertTrue(viewModel.uiState.value.isLoading)
        sheet.complete(null)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
