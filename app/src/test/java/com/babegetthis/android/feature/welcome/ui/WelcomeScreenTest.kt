package com.babegetthis.android.feature.welcome.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.core.onboarding.data.repository.WelcomeRepository
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WelcomeScreenTest {

    @get:Rule val compose = createComposeRule()

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("welcome-screen-test", Context.MODE_PRIVATE)
    private var signedIn = false
    private var guest = false

    @After
    fun clear() {
        prefs.edit().clear().commit()
    }

    private fun show() = compose.setContent {
        WelcomeScreen(
            onSignIn = { signedIn = true },
            onContinueAsGuest = { guest = true },
            viewModel = WelcomeViewModel(WelcomeRepository(prefs) { false }, mockk(relaxed = true)),
        )
    }

    @Test
    fun `both choices are explained`() {
        show()

        compose.onNodeWithText("Save your lists to your account, share them, and add items by voice.").assertExists()
        compose.onNodeWithText("Your lists stay on this phone only.").assertExists()
    }

    @Test
    fun `sign in calls back without continuing as guest`() {
        show()

        compose.onNodeWithText("Sign in").performClick()

        assertTrue(signedIn)
        assertFalse(guest)
    }

    @Test
    fun `continue as guest calls back`() {
        show()

        compose.onNodeWithText("Continue as guest").performClick()

        assertTrue(guest)
        assertFalse(signedIn)
    }
}
