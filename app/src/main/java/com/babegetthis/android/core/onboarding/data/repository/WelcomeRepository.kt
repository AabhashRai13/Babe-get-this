package com.babegetthis.android.core.onboarding.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.babegetthis.android.core.auth.model.AuthState

// Whether to open on the Welcome screen ("Sign in" / "Continue as guest",
// docs/technical-decisions/008). Shown only to a signed-out user who hasn't
// chosen yet. Signed-in users never see it, and signing out asks again —
// signing out removes the account's lists, so it is a fresh start.
//
// isUpdateInstall: true when this install is an update of an older version.
// Those users have been using the app already, so they don't get a welcome.
class WelcomeRepository(private val prefs: SharedPreferences, private val isUpdateInstall: () -> Boolean) {

    fun shouldShowWelcome(authState: AuthState): Boolean = authState is AuthState.Unauthenticated && !isWelcomeDone()

    // "Continue as guest" — the only choice that needs recording. Tapping Sign
    // in doesn't: a signed-in user never sees Welcome, and backing out of Login
    // must land on Welcome again.
    fun markDone() {
        prefs.edit { putBoolean(KEY_WELCOME_DONE, true) }
    }

    // Explicit sign-out and account deletion.
    fun reset() {
        prefs.edit { putBoolean(KEY_WELCOME_DONE, false) }
    }

    // The first answer is persisted rather than recomputed: a fresh install
    // that gets an app update before anyone chose must still be offered the
    // choice, even though it now looks like an update install.
    private fun isWelcomeDone(): Boolean {
        if (!prefs.contains(KEY_WELCOME_DONE)) {
            prefs.edit { putBoolean(KEY_WELCOME_DONE, isUpdateInstall()) }
        }
        return prefs.getBoolean(KEY_WELCOME_DONE, false)
    }

    private companion object {
        const val KEY_WELCOME_DONE = "welcome_done"
    }
}
