package com.babegetthis.android.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.TokenManager
import com.babegetthis.android.core.error.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    // True when the user has typed a different name than what's saved
    val hasNameChanged: Boolean
        get() = editedName.isNotBlank() && editedName != userName
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // One-shot events for showing toast/snack bar messages
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    init {
        // Load persisted user info from TokenManager.
        // If the user just logged in, these will be populated.
        // If they were already logged in from a previous session, same deal.
        val name = tokenManager.getUserName() ?: ""
        val email = tokenManager.getUserEmail() ?: ""
        _uiState.value = ProfileUiState(
            userName = name,
            userEmail = email,
            editedName = name,
        )
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

    // Permanent server-side deletion. Local lists are untouched — they only
    // live on the device. On success the repository clears auth state, which
    // flips the app back to logged-out, same as logout.
    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletingAccount = true)

            when (val result = authRepository.deleteAccount()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isDeletingAccount = false)
                    _toastEvent.emit("Your account has been deleted")
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(isDeletingAccount = false)
                    _toastEvent.emit(result.error.message)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)

            when (val result = authRepository.logout()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoggingOut = false)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(isLoggingOut = false)
                    _toastEvent.emit(result.error.message)
                }
            }
        }
    }
}
