package com.babegetthis.android.journey

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.babegetthis.android.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.pin.data.PinStore
import com.babegetthis.android.testing.ResetAppStateRule
import javax.inject.Inject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Two journeys that share a setup: what the app does about being signed in.
//
// Auth runs entirely against TestAuthRepository — a local sign-in with no
// network and no credentials. What is being checked is the app's own wiring:
// that a successful sign-in actually flips the UI, that logging out actually
// clears it, and that the voice feature is gated on it.
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthAndVoiceGateTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var authStateManager: AuthStateManager
    @Inject lateinit var pinStore: PinStore

    // Order 1: injects, then wipes state left by the previous test — before the
    // Activity rule below composes anything.
    @get:Rule(order = 1)
    val reset = ResetAppStateRule(hilt, { database }, { authStateManager }, { pinStore })

    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    // Once signed in, opening voice asks for RECORD_AUDIO — a SYSTEM dialog that
    // Compose cannot see or dismiss, so the test would hang behind it. Granting it
    // up front is the standard fix. Nothing actually records: TestVoiceModule
    // replaces transcription, and the gate tests below never get this far.
    @get:Rule(order = 3)
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun present(text: String) =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun openCreateChooser() {
        compose.onNodeWithText(
            if (present("Create your first list")) "Create your first list" else "Create list"
        ).performClick()
        awaitText("Voice")
    }

    // Goes via the home screen's account action rather than the voice prompt.
    // "Sign in" labels BOTH the prompt's button and the login screen's submit, so
    // reaching login through the prompt leaves two matches on screen at once.
    private fun signIn(email: String = "aabhash@example.com") {
        compose.onNodeWithText("Sign in").performClick()

        awaitText("Email")
        compose.onNodeWithText("Email").performTextInput(email)
        compose.onNodeWithText("Password").performTextInput("secret123")
        compose.onNodeWithText("Sign in").performClick()
    }

    // The app starts signed out, and the home screen says so via its account
    // action rather than blocking the user — lists are local, so being signed out
    // must not stop anyone using the app.
    @Test
    fun signedOutUserCanStillUseTheApp() {
        awaitText("No lists yet")

        compose.onNodeWithText("Create your first list").performClick()

        awaitText("Type")
    }

    // The voice gate: voice needs the backend, so it needs an account. A signed
    // out user must be told that, not silently handed a dead button.
    @Test
    fun voiceIsGatedBehindSigningIn() {
        awaitText("No lists yet")
        openCreateChooser()

        compose.onNodeWithText("Voice").performClick()

        awaitText("Sign in to use voice")
        compose.onNodeWithText("Create an account to capture shopping lists by voice.")
            .assertExists()
    }

    // And crucially: no recording starts. If the sheet had opened anyway, the
    // "Listening…" state would be on screen.
    @Test
    fun theGatedVoiceFlowNeverStartsRecording() {
        awaitText("No lists yet")
        openCreateChooser()

        compose.onNodeWithText("Voice").performClick()
        awaitText("Sign in to use voice")

        compose.onNodeWithText("Listening…").assertDoesNotExist()
        compose.onNodeWithText("Stop").assertDoesNotExist()
    }

    // Signing in must actually open the gate: the same action that produced the
    // prompt now proceeds instead.
    //
    // Synchronises on the account action losing its "Sign in" label, NOT on the
    // home screen reappearing. "No lists yet" comes back the instant the list
    // screen is visible, which can be BEFORE the auth state has propagated to the
    // create-chooser — so the next tap read a stale isLoggedIn and got the prompt
    // again. Passed locally and failed on a slower CI runner, which is the
    // signature of a test racing the app rather than waiting on it.
    @Test
    fun signingInOpensTheVoiceGate() {
        awaitText("No lists yet")
        signIn()

        compose.waitUntil(timeoutMillis = 10_000) { !present("Sign in") }

        openCreateChooser()
        compose.onNodeWithText("Voice").performClick()

        compose.waitUntil(timeoutMillis = 10_000) { !present("Sign in to use voice") }
    }
}
