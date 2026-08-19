package com.babegetthis.android.core.auth.model

// Represents the user's authentication status across the app.

sealed class AuthState {
    // App just launched, checking if a saved token exists
    data object Loading : AuthState()

    // User is logged in — we have a valid token
    data class Authenticated(val userId: String) : AuthState()

    // No token found or token expired — show login screen
    data object Unauthenticated : AuthState()
}
