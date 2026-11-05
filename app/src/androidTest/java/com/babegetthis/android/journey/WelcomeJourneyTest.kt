package com.babegetthis.android.journey

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babegetthis.android.MainActivity
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.pin.data.PinStore
import com.babegetthis.android.testing.ResetAppStateRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

// First launch, signed out: the Welcome screen (docs/technical-decisions/008).
// The navigation is the risk here — auth was only ever opened from the lists
// screen before, so returning from it assumed the lists were underneath.
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WelcomeJourneyTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var database: AppDatabase

    @Inject lateinit var authStateManager: AuthStateManager

    @Inject lateinit var pinStore: PinStore

    @get:Rule(order = 1)
    val reset = ResetAppStateRule(hilt, { database }, { authStateManager }, { pinStore }, welcomeDone = false)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun continueAsGuestLandsOnLists() {
        awaitText("Continue as guest")

        compose.onNodeWithText("Continue as guest").performClick()

        awaitText("No lists yet")
    }

    @Test
    fun backFromLoginReturnsToWelcome() {
        awaitText("Continue as guest")
        compose.onNodeWithText("Sign in").performClick()
        awaitText("Email")

        compose.onNodeWithContentDescription("Close").performClick()

        awaitText("Continue as guest")
    }

    @Test
    fun signInFromWelcomeLandsOnLists() {
        awaitText("Continue as guest")
        compose.onNodeWithText("Sign in").performClick()
        awaitText("Email")

        compose.onNodeWithText("Email").performTextInput("aabhash@example.com")
        compose.onNodeWithText("Password").performTextInput("secret123")
        compose.onNodeWithText("Sign in").performScrollTo().performClick()

        awaitText("No lists yet")
        compose.onNodeWithText("Continue as guest").assertDoesNotExist()
    }

    // Signing out takes the account's lists off the phone: a fresh start.
    @Test
    fun signingOutReturnsToWelcome() {
        signInFromWelcomeLandsOnLists()

        compose.onNodeWithText("A").performClick() // the avatar: "aabhash"
        awaitText("Log out")
        compose.onNodeWithText("Log out").performClick()

        awaitText("Continue as guest")
    }
}
