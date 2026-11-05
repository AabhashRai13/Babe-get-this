package com.babegetthis.android.core.onboarding.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.core.auth.model.AuthState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Real SharedPreferences under Robolectric: what matters is what survives
// between reads and app updates.
@RunWith(RobolectricTestRunner::class)
class WelcomeRepositoryTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("welcome-test", Context.MODE_PRIVATE)
    private var updateInstall = false
    private val repository = WelcomeRepository(prefs) { updateInstall }

    @After
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `a fresh install signed out sees Welcome`() {
        assertTrue(repository.shouldShowWelcome(AuthState.Unauthenticated))
    }

    // Existing users updating to this version have already been using the app.
    @Test
    fun `an updated install never sees Welcome`() {
        updateInstall = true

        assertFalse(repository.shouldShowWelcome(AuthState.Unauthenticated))
    }

    // A fresh install that gets an app update before anyone chose still owes
    // the user the choice: the first answer is persisted, not recomputed.
    @Test
    fun `the first answer sticks across a later app update`() {
        repository.shouldShowWelcome(AuthState.Unauthenticated)

        updateInstall = true

        assertTrue(repository.shouldShowWelcome(AuthState.Unauthenticated))
    }

    @Test
    fun `a signed-in, expired or still-loading user never sees Welcome`() {
        assertFalse(repository.shouldShowWelcome(AuthState.Authenticated("u1")))
        assertFalse(repository.shouldShowWelcome(AuthState.SessionExpired("u1")))
        assertFalse(repository.shouldShowWelcome(AuthState.Loading))
    }

    @Test
    fun `continuing as guest hides Welcome and signing out shows it again`() {
        repository.markDone()
        assertFalse(repository.shouldShowWelcome(AuthState.Unauthenticated))

        repository.reset()
        assertTrue(repository.shouldShowWelcome(AuthState.Unauthenticated))
    }

    @Test
    fun `signing out shows Welcome even on an updated install`() {
        updateInstall = true

        repository.reset()

        assertTrue(repository.shouldShowWelcome(AuthState.Unauthenticated))
    }
}
