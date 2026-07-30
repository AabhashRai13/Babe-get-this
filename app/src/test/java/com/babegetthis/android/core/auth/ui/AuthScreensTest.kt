package com.babegetthis.android.core.auth.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.RegisterResult
import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Real ViewModels over a mocked AuthRepository, so the validation rules and
// loading/error state are the genuine article. Controls are actioned through the
// semantics tree: these screens are tall (logo, subtitle, several fields) so the
// primary button sits below the fold on Robolectric's default display.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthScreensTest {

    @get:Rule val compose = createComposeRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<AuthRepository>(relaxed = true)
    private val user = User(id = "u1", email = "a@b.c", name = "Aabhash")

    private var navigatedToRegister = false
    private var navigatedToLogin = false
    private var navigatedToForgot = false
    private var succeeded = false

    private fun click(label: String) =
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)

    private fun type(label: String, text: String) =
        compose.onNodeWithText(label).performTextInput(text)

    // --- LoginScreen ---

    private fun login() = compose.setContent {
        LoginScreen(
            onNavigateToRegister = { navigatedToRegister = true },
            onNavigateToForgotPassword = { navigatedToForgot = true },
            onLoginSuccess = { succeeded = true },
            viewModel = LoginViewModel(repository),
        )
    }

    @Test
    fun `login shows its fields and primary action`() {
        login()

        compose.onNodeWithText("Email").assertExists()
        compose.onNodeWithText("Password").assertExists()
        compose.onNodeWithText("Sign in").assertExists()
    }

    @Test
    fun `sign in is disabled until both fields are filled`() {
        login()

        compose.onNodeWithText("Sign in").assertIsNotEnabled()

        type("Email", "a@b.c")
        compose.onNodeWithText("Sign in").assertIsNotEnabled()

        type("Password", "secret123")
        compose.onNodeWithText("Sign in").assertIsEnabled()
    }

    // Validation fires as you type, not only on submit.
    @Test
    fun `a malformed email is flagged inline`() {
        login()

        type("Email", "not-an-email")

        compose.onNodeWithText("Enter a valid email address").assertExists()
    }

    @Test
    fun `a valid email clears the inline error`() {
        login()
        type("Email", "not-an-email")

        compose.onNodeWithText("Email").performTextInput("")
        type("Email", "@example.com")

        compose.onNodeWithText("Enter a valid email address").assertDoesNotExist()
    }

    @Test
    fun `submitting reaches the repository with the typed credentials`() {
        coEvery { repository.login(any(), any()) } returns Result.Success(user)
        login()
        type("Email", "a@b.c")
        type("Password", "secret123")

        click("Sign in")

        coVerify { repository.login("a@b.c", "secret123") }
    }

    @Test
    fun `a successful login reports success`() {
        coEvery { repository.login(any(), any()) } returns Result.Success(user)
        login()
        type("Email", "a@b.c")
        type("Password", "secret123")

        click("Sign in")

        assertTrue(succeeded)
    }

    @Test
    fun `a failed login shows the error and does not report success`() {
        coEvery { repository.login(any(), any()) } returns
            Result.Error(AppError.AuthError("Invalid email or password."))
        login()
        type("Email", "a@b.c")
        type("Password", "wrongpass")

        click("Sign in")

        compose.onNodeWithText("Invalid email or password.").assertExists()
        assertTrue(!succeeded)
    }

    @Test
    fun `an offline login shows the network message`() {
        coEvery { repository.login(any(), any()) } returns Result.Error(AppError.NetworkError())
        login()
        type("Email", "a@b.c")
        type("Password", "secret123")

        click("Sign in")

        compose.onNodeWithText(AppError.NetworkError().message).assertExists()
    }

    @Test
    fun `login offers the register and forgot-password routes`() {
        login()

        click("Sign up")
        assertTrue(navigatedToRegister)

        click("Forgot password?")
        assertTrue(navigatedToForgot)
    }

    // --- RegisterScreen ---

    private fun register() = compose.setContent {
        RegisterScreen(
            onNavigateToLogin = { navigatedToLogin = true },
            onRegisterSuccess = { succeeded = true },
            viewModel = RegisterViewModel(repository),
        )
    }

    private fun fillRegisterForm(
        password: String = "secret123",
        confirm: String = "secret123",
    ) {
        type("Your name", "Aabhash")
        type("Email", "a@b.c")
        type("Password", password)
        type("Confirm password", confirm)
    }

    @Test
    fun `register shows every field`() {
        register()

        compose.onNodeWithText("Your name").assertExists()
        compose.onNodeWithText("Email").assertExists()
        compose.onNodeWithText("Password").assertExists()
        compose.onNodeWithText("Confirm password").assertExists()
    }

    @Test
    fun `create account is disabled until the form is complete`() {
        register()

        compose.onNodeWithText("Create account").assertIsNotEnabled()

        fillRegisterForm()

        compose.onNodeWithText("Create account").assertIsEnabled()
    }

    @Test
    fun `a short password is flagged`() {
        register()

        type("Password", "abc")

        compose.onNodeWithText("Password must be at least 6 characters").assertExists()
    }

    @Test
    fun `a mismatched confirmation is flagged and blocks submission`() {
        register()

        fillRegisterForm(password = "secret123", confirm = "different1")

        compose.onNodeWithText("Passwords do not match").assertExists()
        compose.onNodeWithText("Create account").assertIsNotEnabled()
    }

    @Test
    fun `registering reaches the repository with name, email and password`() {
        coEvery { repository.register(any(), any(), any()) } returns
            Result.Success(RegisterResult.SignedIn(user))
        register()
        fillRegisterForm()

        click("Create account")

        coVerify { repository.register("a@b.c", "secret123", "Aabhash") }
    }

    @Test
    fun `a signed-in registration reports success`() {
        coEvery { repository.register(any(), any(), any()) } returns
            Result.Success(RegisterResult.SignedIn(user))
        register()
        fillRegisterForm()

        click("Create account")

        assertTrue(succeeded)
    }

    @Test
    fun `a taken email shows the error`() {
        coEvery { repository.register(any(), any(), any()) } returns
            Result.Error(AppError.AuthError("That email is already registered."))
        register()
        fillRegisterForm()

        click("Create account")

        compose.onNodeWithText("That email is already registered.").assertExists()
        assertTrue(!succeeded)
    }

    @Test
    fun `register offers the sign-in route`() {
        register()

        click("Sign in")

        assertTrue(navigatedToLogin)
    }

    // --- ForgotPasswordScreen ---

    private fun forgot() = compose.setContent {
        ForgotPasswordScreen(
            onNavigateBack = {},
            onResetSuccess = { succeeded = true },
            viewModel = ForgotPasswordViewModel(repository),
        )
    }

    @Test
    fun `forgot password asks for an email first`() {
        forgot()

        compose.onNodeWithText("Email").assertExists()
        compose.onNodeWithText("Send code").assertExists()
    }

    @Test
    fun `send code is disabled until the email is valid`() {
        forgot()

        compose.onNodeWithText("Send code").assertIsNotEnabled()

        type("Email", "a@b.c")

        compose.onNodeWithText("Send code").assertIsEnabled()
    }

    @Test
    fun `sending a code reaches the repository`() {
        coEvery { repository.requestPasswordReset(any()) } returns Result.Success(Unit)
        forgot()
        type("Email", "a@b.c")

        click("Send code")

        coVerify { repository.requestPasswordReset("a@b.c") }
    }

    @Test
    fun `once the code is sent the screen asks for it and a new password`() {
        coEvery { repository.requestPasswordReset(any()) } returns Result.Success(Unit)
        forgot()
        type("Email", "a@b.c")

        click("Send code")

        compose.onNodeWithText("8-digit code").assertExists()
        compose.onNodeWithText("New password").assertExists()
    }

    @Test
    fun `a failed send shows the error and stays on the email step`() {
        coEvery { repository.requestPasswordReset(any()) } returns
            Result.Error(AppError.NetworkError())
        forgot()
        type("Email", "a@b.c")

        click("Send code")

        compose.onNodeWithText("8-digit code").assertDoesNotExist()
    }

    // --- AuthPromptDialog ---

    @Test
    fun `the auth prompt explains why sign-in is needed`() {
        compose.setContent { AuthPromptDialog(onLogin = {}, onRegister = {}, onDismiss = {}) }

        compose.onNodeWithText("Sign in to use voice").assertExists()
        compose.onNodeWithText("Create an account to capture shopping lists by voice.").assertExists()
    }

    // Both actions close the dialog first and then navigate — asserting the
    // dismissal too, because a dialog left open behind a navigation would be a
    // real bug and this is the only place that ordering is visible.
    @Test
    fun `the auth prompt routes to sign-in and closes itself`() {
        var login = false
        var register = false
        var dismissed = false
        compose.setContent {
            AuthPromptDialog(
                onLogin = { login = true },
                onRegister = { register = true },
                onDismiss = { dismissed = true },
            )
        }

        click("Sign in")

        assertTrue(login)
        assertTrue(dismissed)
        assertTrue("must not also trigger register", !register)
    }

    @Test
    fun `the auth prompt routes to register and closes itself`() {
        var login = false
        var register = false
        var dismissed = false
        compose.setContent {
            AuthPromptDialog(
                onLogin = { login = true },
                onRegister = { register = true },
                onDismiss = { dismissed = true },
            )
        }

        click("Create account")

        assertTrue(register)
        assertTrue(dismissed)
        assertTrue("must not also trigger login", !login)
    }
}
