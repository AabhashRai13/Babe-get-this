package com.babegetthis.android.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.onboarding.data.repository.WelcomeRepository
import com.babegetthis.android.core.sync.data.repository.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

// Holds the profile bottom sheet's UI state.
// Like a Riverpod StateNotifier — tracks what's being displayed and edited.

data class ProfileUiState(
    val userName: String = "",
    val userEmail: String = "",
    val editedName: String = "",
    val isSaving: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    // Some changes never reached the server; signing out now would lose them.
    val showUnsyncedSignOutWarning: Boolean = false,
) {
    // True when the user has typed a different name than what's saved
    val hasNameChanged: Boolean
        get() = editedName.isNotBlank() && editedName != userName
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authStateManager: AuthStateManager,
    private val syncEngine: SyncEngine,
    private val welcome: WelcomeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // One-shot events for showing toast/snack bar messages
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    init {
        // Observe the current user instead of snapshotting it once. This
        // ViewModel is scoped to the shopping-list start destination, so its
        // instance outlives a logout→login: a one-time init read would keep
        // showing the previous account's name/email. Collecting the flow means
        // switching accounts refreshes the sheet. (email is written before the
        // name flow emits in AuthStateManager.login, so it's already current.)
        viewModelScope.launch {
            combine(
                authStateManager.userName,
                authStateManager.userEmail,
            ) { name, email -> (name ?: "") to (email ?: "") }
                .collect { (name, email) ->
                    _uiState.value = _uiState.value.copy(
                        userName = name,
                        userEmail = email,
                        editedName = name,
                    )
                }
        }
    }

    // Called as the user types in the name field
    fun onNameChanged(newName: String) {
        _uiState.value = _uiState.value.copy(editedName = newName)
    }

    // Save the edited name to the backend (and locally)
    fun saveName() {
        val newName = _uiState.value.editedName.trim()
        if (newName.isBlank()) {
            viewModelScope.launch {
                _toastEvent.emit("Name cannot be empty")
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            when (val result = authRepository.updateUserName(newName)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        userName = result.data.name,
                        editedName = result.data.name,
                        isSaving = false,
                    )
                    _toastEvent.emit("Name updated")
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _toastEvent.emit(result.error.message)
                }
            }
        }
    }

    // Permanent server-side deletion. The account's lists leave the phone with
    // it (docs/technical-decisions/008). On success the repository clears auth
    // state, which flips the app back to logged-out and onto Welcome, same as
    // logout.
    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletingAccount = true)

            // Before, not after: the final best-effort push inside needs the
            // session that deleteAccount is about to destroy. If deletion then
            // fails, discovery on the next sync kick restores the lists.
            syncEngine.evictAccountLists()

            when (val result = authRepository.deleteAccount()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isDeletingAccount = false)
                    welcome.reset()
                    _toastEvent.emit("Your account has been deleted")
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(isDeletingAccount = false)
                    _toastEvent.emit(result.error.message)
                }
            }
        }
    }

    // Signing out takes every account list off the phone. Push first; if
    // anything still hasn't reached the server (offline), ask before throwing
    // it away — with every list an account list, that is no longer the rare
    // case decision 004 accepted losing silently.
    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            syncEngine.push()
            if (syncEngine.hasUnsyncedChanges()) {
                _uiState.value = _uiState.value.copy(isLoggingOut = false, showUnsyncedSignOutWarning = true)
            } else {
                completeSignOut()
            }
        }
    }

    fun confirmSignOutAnyway() {
        _uiState.value = _uiState.value.copy(showUnsyncedSignOutWarning = false, isLoggingOut = true)
        viewModelScope.launch { completeSignOut() }
    }

    fun dismissUnsyncedSignOutWarning() {
        _uiState.value = _uiState.value.copy(showUnsyncedSignOutWarning = false)
    }

    private suspend fun completeSignOut() {
        // The EXPLICIT sign-out path only — session loss in BabeGetThisApp
        // never evicts. Runs before logout so the final push still has a
        // session; if logout then fails, the next sync kick's discovery
        // restores the lists.
        syncEngine.evictAccountLists()

        when (val result = authRepository.logout()) {
            is Result.Success -> {
                _uiState.value = _uiState.value.copy(isLoggingOut = false)
                // The next launch starts on Welcome too; navigation reacts to
                // AuthStateManager.signOuts for this one.
                welcome.reset()
            }
            is Result.Error -> {
                _uiState.value = _uiState.value.copy(isLoggingOut = false)
                _toastEvent.emit(result.error.message)
            }
        }
    }
}
