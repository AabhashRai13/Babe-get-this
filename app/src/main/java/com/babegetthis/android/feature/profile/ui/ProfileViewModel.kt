package com.babegetthis.android.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.di.ApplicationScope
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.onboarding.data.repository.WelcomeRepository
import com.babegetthis.android.core.sync.data.repository.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    // Signing out and deleting the account run here, NOT on viewModelScope:
    // both flip the auth state, and navigation reacts by popping the screen
    // that owns this ViewModel (BgtNavGraph, signOuts). viewModelScope is then
    // cancelled, and whatever hadn't finished — taking the account's lists off
    // the phone, resetting Welcome — would silently never happen.
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    // The application scope's own dispatcher is Default. Sign-out and
    // deletion stay on Main, as they were on viewModelScope: the auth state
    // they flip drives navigation, and AuthStateManager's contract is that
    // sign-in and sign-out happen on Main while only session LOSS arrives
    // from a background thread.
    private fun launchOutlivingThisScreen(block: suspend () -> Unit) =
        applicationScope.launch(Dispatchers.Main.immediate) { block() }

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
        launchOutlivingThisScreen {
            _uiState.value = _uiState.value.copy(isDeletingAccount = true)

            // Push while the session still exists, delete the account, and only
            // then take its lists off the phone. A deletion that fails (offline,
            // server error) must leave every list and unsynced edit in place.
            syncEngine.push()

            when (val result = authRepository.deleteAccount()) {
                is Result.Success -> {
                    syncEngine.evictAccountLists()
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
        launchOutlivingThisScreen {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            syncEngine.push()
            // The owner, not the live session: after a lost session the push
            // above was a no-op, and this is exactly when the queue is full.
            val owner = authStateManager.dataOwnerId()
            if (owner != null && syncEngine.hasUnsyncedChanges(owner)) {
                _uiState.value = _uiState.value.copy(isLoggingOut = false, showUnsyncedSignOutWarning = true)
            } else {
                completeSignOut()
            }
        }
    }

    fun confirmSignOutAnyway() {
        _uiState.value = _uiState.value.copy(showUnsyncedSignOutWarning = false, isLoggingOut = true)
        launchOutlivingThisScreen { completeSignOut() }
    }

    fun dismissUnsyncedSignOutWarning() {
        _uiState.value = _uiState.value.copy(showUnsyncedSignOutWarning = false)
    }

    private suspend fun completeSignOut() {
        // The EXPLICIT sign-out path only — session loss in BabeGetThisApp
        // never evicts. The sign-out runs inside the eviction's critical
        // section: the final push still has a session, and no sync kick can
        // slip in between and land the lists again (SyncEngine.evictAccountLists).
        var signOut: Result<Unit> = Result.Success(Unit)
        val evicted = syncEngine.evictAccountLists { signOut = authRepository.logout() }

        when (val result = if (evicted is Result.Error) evicted else signOut) {
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
