package com.babegetthis.android.core.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.RegisterResult
import com.babegetthis.android.core.error.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_PASSWORD_LENGTH = 6

data class RegisterUiState(
    // The ViewModel owns the field values so it can validate them — the UI
    // just renders these and forwards keystrokes (keeps the UI dumb).
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    // Per-field error text to show under each field. null = nothing to show.
    // These are only filled once a field has been touched (is non-empty), so
    // we don't yell "invalid email" before the user has typed anything.
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    // Async / server state. errorMessage now only carries server-side results,
    // not field validation — those are inline above.
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    // Derived: drives the submit button. Computed from the raw values (not the
    // *Error fields) because errors stay null until a field is touched, but the
    // button must stay disabled even on a pristine, empty form.
    val isFormValid: Boolean
        get() = name.isNotBlank() &&
            isValidEmail(email) &&
            password.length >= MIN_PASSWORD_LENGTH &&
            confirmPassword == password
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    // One-shot event that fires after a successful registration.
    private val _registerSuccess = MutableSharedFlow<Unit>()
    val registerSuccess = _registerSuccess.asSharedFlow()

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    fun onEmailChange(value: String) {
        val error = if (value.isNotEmpty() && !isValidEmail(value)) {
            "Enter a valid email address"
        } else {
            null
        }
        _uiState.value = _uiState.value.copy(email = value, emailError = error)
    }

    fun onPasswordChange(value: String) {
        val passwordError = if (value.isNotEmpty() && value.length < MIN_PASSWORD_LENGTH) {
            "Password must be at least $MIN_PASSWORD_LENGTH characters"
        } else {
            null
        }
        val current = _uiState.value
        _uiState.value = current.copy(
            password = value,
            passwordError = passwordError,
            // Confirm depends on password, so re-check it whenever password changes.
            confirmPasswordError = confirmMismatchError(value, current.confirmPassword),
        )
    }

    fun onConfirmPasswordChange(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            confirmPassword = value,
            confirmPasswordError = confirmMismatchError(current.password, value),
        )
    }

    // Only flag a mismatch once the user has typed something in confirm.
    private fun confirmMismatchError(password: String, confirm: String): String? =
        if (confirm.isNotEmpty() && confirm != password) "Passwords do not match" else null

    fun register() {
        val state = _uiState.value
        // The button is disabled when invalid, but guard here too as a safety net.
        if (!state.isFormValid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = authRepository.register(state.email, state.password, state.name)) {
                is Result.Success -> when (result.data) {
                    is RegisterResult.SignedIn -> {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        _registerSuccess.emit(Unit)
                    }
                    RegisterResult.ConfirmationRequired -> {
                        // Not an error — the account was created, they just need to confirm.
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Check your email to confirm your account, then sign in.",
                        )
                    }
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
