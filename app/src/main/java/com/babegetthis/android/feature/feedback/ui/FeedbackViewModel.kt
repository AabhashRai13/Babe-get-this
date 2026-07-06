package com.babegetthis.android.feature.feedback.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.feedback.data.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Form state + submit for the feedback sheet. Mirrors the ProfileViewModel
// pattern: a single UiState StateFlow the sheet renders, plus a one-shot
// toast event the host's SnackbarHost shows.
@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
) : ViewModel() {

    data class FeedbackUiState(
        val liked: String = "",
        val disliked: String = "",
        // null until the user picks Yes or No — it's the one required answer,
        // so we can't default it without putting words in their mouth.
        val wouldUseApp: Boolean? = null,
        val improvements: String = "",
        val isSubmitting: Boolean = false,
    ) {
        val canSubmit: Boolean get() = wouldUseApp != null && !isSubmitting
    }

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    // One-shot "close the sheet" signal after a successful submit. An event, not
    // a UiState flag: this ViewModel outlives the sheet (it's scoped to the
    // screen), so a sticky isSubmitted=true flag would instantly re-close the
    // sheet the next time it's opened.
    private val _dismissEvent = MutableSharedFlow<Unit>()
    val dismissEvent = _dismissEvent.asSharedFlow()

    fun onLikedChanged(value: String) = _uiState.update { it.copy(liked = value) }
    fun onDislikedChanged(value: String) = _uiState.update { it.copy(disliked = value) }
    fun onWouldUseAppChanged(value: Boolean) = _uiState.update { it.copy(wouldUseApp = value) }
    fun onImprovementsChanged(value: String) = _uiState.update { it.copy(improvements = value) }

    fun submit() {
        val current = _uiState.value
        // canSubmit gates the button, but re-check here so a stale click can't
        // slip through (e.g. double-tap racing the isSubmitting flip).
        val wouldUseApp = current.wouldUseApp ?: return
        if (current.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = feedbackRepository.submitFeedback(
                liked = current.liked,
                disliked = current.disliked,
                wouldUse = wouldUseApp,
                improvements = current.improvements,
            )
            when (result) {
                is Result.Success -> {
                    _toastEvent.emit("Thanks — feedback sent!")
                    // Wipe the form so reopening the sheet starts fresh — the VM
                    // outlives the sheet, so old answers would linger otherwise.
                    _uiState.value = FeedbackUiState()
                    _dismissEvent.emit(Unit)
                }
                is Result.Error -> {
                    // Keep the sheet open with everything they typed intact,
                    // so a flaky connection doesn't eat their answers.
                    _toastEvent.emit(result.error.message)
                    _uiState.update { it.copy(isSubmitting = false) }
                }
            }
        }
    }
}
