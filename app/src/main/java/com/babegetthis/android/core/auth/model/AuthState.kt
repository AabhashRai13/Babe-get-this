package com.babegetthis.android.core.auth.model

// Represents the user's authentication status across the app.

sealed class AuthState {
    // App just launched, checking if a saved token exists
    data object Loading : AuthState()

    // User is logged in — we have a valid token
    data class Authenticated(val userId: String) : AuthState()

    // The session was lost (token expired or revoked) but the user never signed
    // out. Their lists stay on screen and editable — only server features
    // (sync, share, join, voice) wait for a fresh sign-in. See
    // docs/technical-decisions/008.
    data class SessionExpired(val userId: String) : AuthState()

    // No token found or token expired — show login screen
    data object Unauthenticated : AuthState()
}
