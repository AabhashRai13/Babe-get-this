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

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
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

    private fun confirmMismatchError(password: String, confirm: String): String? =
        if (confirm.isNotEmpty() && confirm != password) "Passwords do not match" else null

    fun register() {
        val state = _uiState.value
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
