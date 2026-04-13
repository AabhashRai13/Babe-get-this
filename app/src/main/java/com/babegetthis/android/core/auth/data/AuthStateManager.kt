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

    // Call this once when the app starts (e.g., from MainActivity or Application)
    fun initialize() {
        val token = tokenManager.getToken()
        val userId = tokenManager.getUserId()

        _authState.value = if (token != null && userId != null) {
            AuthState.Authenticated(userId)
        } else {
            AuthState.Unauthenticated
        }
    }

    // Called after successful login or register
    fun login(token: String, userId: String) {
        tokenManager.saveToken(token)
        tokenManager.saveUserId(userId)
        _authState.value = AuthState.Authenticated(userId)
    }

    // Called on explicit logout or when server returns 401
    fun logout() {
        tokenManager.clear()
        _authState.value = AuthState.Unauthenticated
    }
}
