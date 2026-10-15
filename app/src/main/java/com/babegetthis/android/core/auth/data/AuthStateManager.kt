package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.auth.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Single source of truth for "is the user logged in?".
// Like an AuthNotifier in Riverpod — the whole app reacts to changes.
//
// On app start, it checks TokenManager for a saved token.
// Navigation observes authState to decide: show login or show main app.
// A lost session (Supabase gives up, or a 401) calls expireSession(), not
// logout(): the account's lists stay on screen until the user signs in again
// (docs/technical-decisions/008).

@Singleton
class AuthStateManager @Inject constructor(private val tokenManager: TokenManager) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Whose lists this device shows: the account while signed in or after a
    // lost session, nobody (guest lists) when signed out. Loading is skipped so
    // nothing renders guest lists for the instant before initialize() runs.
    val dataOwner: Flow<String?> = authState
        .filter { it !is AuthState.Loading }
        .map { it.ownerId() }
        .distinctUntilChanged()

    fun dataOwnerId(): String? = _authState.value.ownerId()

    private fun AuthState.ownerId(): String? = when (this) {
        is AuthState.Authenticated -> userId
        is AuthState.SessionExpired -> userId
        AuthState.Loading, AuthState.Unauthenticated -> null
    }

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

        _authState.value = when {
            userId == null -> AuthState.Unauthenticated
            token == null -> AuthState.SessionExpired(userId)
            else -> AuthState.Authenticated(userId)
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

    // The session is gone but the user never signed out. Keeps the user id (and
    // with it the account's lists) and drops only the dead token. A no-op when
    // no user id is stored, so it can never undo an explicit sign-out, which
    // fires this same event when it clears the Supabase session. In Loading,
    // storage is updated and initialize() derives SessionExpired from it.
    fun expireSession() {
        val userId = tokenManager.getUserId() ?: return
        tokenManager.clearToken()
        if (_authState.value !is AuthState.Loading) {
            _authState.value = AuthState.SessionExpired(userId)
        }
    }

    // Called on explicit sign-out and account deletion only.
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
