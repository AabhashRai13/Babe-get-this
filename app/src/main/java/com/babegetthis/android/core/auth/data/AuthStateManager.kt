package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.auth.model.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Single source of truth for "is the user logged in?".
// Like an AuthNotifier in Riverpod — the whole app reacts to changes.
//
// On app start, it checks TokenManager for a saved token.
// Navigation observes authState to decide: show login or show main app.
// On 401 from server, AuthAuthenticator calls logout() → nav auto-redirects to login.

@Singleton
class AuthStateManager @Inject constructor(
    private val tokenManager: TokenManager,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // The signed-in user's display name, or null when logged out.
    // Kept as its own flow because AuthState only carries the userId — the UI
    // (e.g. the Home greeting) observes this so the name shows up reactively
    // on login, on logout, and right after the user edits it.
    private val _userName = MutableStateFlow(tokenManager.getUserName())
    val userName: StateFlow<String?> = _userName.asStateFlow()

    // The signed-in user's email, exposed reactively for the same reason as the
    // name. Two accounts can share a display name, so the profile screen must
    // react to the email changing on its own — not piggyback on the name flow.
    private val _userEmail = MutableStateFlow(tokenManager.getUserEmail())
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    // Call this once when the app starts (e.g., from MainActivity or Application)
    fun initialize() {
        val token = tokenManager.getToken()
        val userId = tokenManager.getUserId()

        _authState.value = if (token != null && userId != null) {
            AuthState.Authenticated(userId)
        } else {
            AuthState.Unauthenticated
        }
        _userName.value = tokenManager.getUserName()
        _userEmail.value = tokenManager.getUserEmail()
    }

    // Called after successful login or register.
    // Persists token, user ID, name, and email so they survive app restarts.
    fun login(token: String, userId: String, userName: String, userEmail: String) {
        tokenManager.saveToken(token)
        tokenManager.saveUserId(userId)
        tokenManager.saveUserName(userName)
        tokenManager.saveUserEmail(userEmail)
        _authState.value = AuthState.Authenticated(userId)
        _userName.value = userName
        _userEmail.value = userEmail
    }

    // Supabase auto-refreshes the session in the background and rotates the
    // access token. Persist just the rotated token (no authState/name change)
    // so AuthInterceptor stops sending the stale one cached at login.
    fun refreshToken(token: String) {
        tokenManager.saveToken(token)
    }

    // Called on explicit logout or when server returns 401
    fun logout() {
        tokenManager.clear()
        _authState.value = AuthState.Unauthenticated
        _userName.value = null
        _userEmail.value = null
    }

    // Update just the cached display name (after the user edits their profile).
    // The repo goes through here instead of touching TokenManager directly,
    // so every local-storage write stays behind this one class.
    fun updateName(name: String) {
        tokenManager.saveUserName(name)
        _userName.value = name
    }

    // The currently cached email, if any — used as a fallback when building a User.
    fun currentEmail(): String? = tokenManager.getUserEmail()
}
