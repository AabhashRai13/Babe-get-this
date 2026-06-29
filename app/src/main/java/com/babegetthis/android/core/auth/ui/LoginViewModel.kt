package com.babegetthis.android.core.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.error.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// UI state for the login screen.
// Like a Riverpod AsyncValue — tracks loading, success, and error states.
data class LoginUiState(
    // The ViewModel owns the field values so it can validate them (dumb UI).
    val email: String = "",
    val password: String = "",
    // Inline email error to show under the field. null = nothing to show.
    // Only filled once the field is touched, so a pristine form stays quiet.
    val emailError: String? = null,
    // Async / server state. errorMessage now only carries server results.
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    // Drives the login button. Login is simpler than register: just a valid
    // email and a non-empty password (we don't enforce length on sign-in —
    // the server decides if the password is correct).
    val isFormValid: Boolean
        get() = isValidEmail(email) && password.isNotBlank()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // One-shot event that fires after a successful login.
    // The screen observes this to trigger navigation (e.g., popBackStack).
    private val _loginSuccess = MutableSharedFlow<Unit>()
    val loginSuccess = _loginSuccess.asSharedFlow()

    fun onEmailChange(value: String) {
        val error = if (value.isNotEmpty() && !isValidEmail(value)) {
            "Enter a valid email address"
        } else {
            null
        }
        _uiState.value = _uiState.value.copy(email = value, emailError = error)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun login() {
        val state = _uiState.value
        // The button is disabled when invalid, but guard here too as a safety net.
        if (!state.isFormValid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = authRepository.login(state.email, state.password)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _loginSuccess.emit(Unit)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.error.message,
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
