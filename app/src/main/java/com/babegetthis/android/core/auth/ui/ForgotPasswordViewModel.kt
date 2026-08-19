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

private const val CODE_LENGTH = 8

// One screen, two steps: enter email → enter code + new password.
// codeSent flips the UI from step one to step two.
data class ForgotPasswordUiState(
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val codeSent: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmailValid: Boolean
        get() = isValidEmail(email)

    val isResetFormValid: Boolean
        get() = code.length == CODE_LENGTH && newPassword.length >= MIN_PASSWORD_LENGTH
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _resetSuccess = MutableSharedFlow<Unit>()
    val resetSuccess = _resetSuccess.asSharedFlow()

    fun onEmailChange(value: String) {
        val error = if (value.isNotEmpty() && !isValidEmail(value)) {
            "Enter a valid email address"
        } else {
            null
        }
        _uiState.value = _uiState.value.copy(email = value, emailError = error)
    }

    fun onCodeChange(value: String) {
        // The code is digits only — drop anything else the keyboard lets through.
        _uiState.value = _uiState.value.copy(
            code = value.filter { it.isDigit() }.take(CODE_LENGTH),
        )
    }

    fun onNewPasswordChange(value: String) {
        val error = if (value.isNotEmpty() && value.length < MIN_PASSWORD_LENGTH) {
            "Password must be at least $MIN_PASSWORD_LENGTH characters"
        } else {
            null
        }
        _uiState.value = _uiState.value.copy(newPassword = value, passwordError = error)
    }

    fun sendCode() {
        val state = _uiState.value
        if (!state.isEmailValid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = authRepository.requestPasswordReset(state.email)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, codeSent = true)
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

    fun resetPassword() {
        val state = _uiState.value
        if (!state.isResetFormValid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = authRepository.resetPassword(state.email, state.code, state.newPassword)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _resetSuccess.emit(Unit)
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
