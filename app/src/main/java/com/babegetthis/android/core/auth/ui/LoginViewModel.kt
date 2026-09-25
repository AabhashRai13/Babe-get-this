package com.babegetthis.android.core.auth.ui

import android.content.Context
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.GOOGLE_SIGN_IN_FAILED
import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.CrashReporter
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isFormValid: Boolean
        get() = isValidEmail(email) && password.isNotBlank()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val analytics: AnalyticsRepository,
    private val googleTokens: GoogleTokenRequester,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

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
        if (!state.isFormValid) return
        signIn { authRepository.login(state.email, state.password) }
    }

    // The whole Google flow. It runs in viewModelScope rather than the screen's
    // scope so rotating mid-sheet doesn't drop the chosen account; the Activity
    // context is only held for the length of the sheet, never stored.
    fun signInWithGoogle(activityContext: Context) = signIn {
        val token = try {
            googleTokens.request(activityContext)
        } catch (e: GoogleSignInOfflineException) {
            return@signIn Result.Error(AppError.NetworkError())
        } catch (e: NoCredentialException) {
            // No Google account on the device: the user's to fix, not a defect.
            return@signIn Result.Error(AppError.AuthError("Add a Google account to this device, then try again."))
        } catch (e: GetCredentialException) {
            return@signIn googleSheetFailed(e)
        } catch (e: GoogleIdTokenParsingException) {
            return@signIn googleSheetFailed(e)
        }
        // null: the user closed the sheet, which is not an error.
        token?.let { authRepository.signInWithGoogle(it.idToken, it.rawNonce) }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // No Play services, or an OAuth client missing this build's SHA-1. Reported
    // as UnknownError because ErrorReportingPolicy drops AuthError, and a
    // Play-only misconfiguration would otherwise surface nowhere but a snackbar.
    private fun googleSheetFailed(e: Exception): Result<User> {
        val error = AppError.UnknownError(GOOGLE_SIGN_IN_FAILED)
        crashReporter.recordNonFatal(e, error)
        return Result.Error(error)
    }

    // Shared by both sign-in paths; a null result means the user backed out.
    // isLoading flips before launching, so a double tap can't open two Google
    // sheets or race an email login.
    private fun signIn(call: suspend () -> Result<User>?) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = call()) {
                null -> _uiState.value = _uiState.value.copy(isLoading = false)
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    // The event carries nothing. The email is exactly the kind
                    // of value that must never be attached, and identity is set
                    // separately from the Supabase session as a UUID.
                    analytics.track(AnalyticsEvent.AccountLoggedIn)
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
}
