package com.babegetthis.android.core.voice.ui.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.voice.data.AudioRecorder
import com.babegetthis.android.core.voice.data.repository.VoiceRepository
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.core.voice.model.VoiceCaptureUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Orchestrates the whole voice-capture flow:
//   record → transcribe → review → persist → navigate.
//
// On purpose, this ViewModel only knows about AudioRecorder + VoiceRepository.
// It does NOT depend on ShoppingListRepository — the screen passes a `persist`
// lambda into confirm(...) so this module stays reusable (a future "add items
// to an existing list" feature could reuse it with a different persist lambda).
@HiltViewModel
class VoiceCaptureViewModel @Inject constructor(
    private val recorder: AudioRecorder,
    private val voiceRepository: VoiceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceCaptureUiState>(VoiceCaptureUiState.Idle)
    val state: StateFlow<VoiceCaptureUiState> = _state.asStateFlow()

    // One-shot navigation event — the screen collects this and navigates to the
    // new list. SharedFlow (not StateFlow) so re-subscribing doesn't re-fire it.
    private val _navigateToList = MutableSharedFlow<String>()
    val navigateToList = _navigateToList.asSharedFlow()

    // Called by the screen after the system permission dialog resolves.
    // If denied, we sit in NeedsPermission so the UI can show an explainer.
    fun onPermissionResult(granted: Boolean) {
        _state.value = if (granted) VoiceCaptureUiState.Idle else VoiceCaptureUiState.NeedsPermission
    }

    fun startRecording() {
        viewModelScope.launch {
            _state.value = VoiceCaptureUiState.Recording()
            recorder.start()
            // Elapsed-time tick intentionally omitted for v1 — add later if the
            // UI grows a timer/waveform. Recording state alone is enough today.
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            _state.value = VoiceCaptureUiState.Transcribing
            val file = recorder.stop()
            when (val result = voiceRepository.transcribeAndParse(file)) {
                is Result.Success -> {
                    _state.value = if (result.data.isEmpty()) {
                        VoiceCaptureUiState.Failed("Didn't catch any items. Try again?")
                    } else {
                        VoiceCaptureUiState.Reviewing(result.data)
                    }
                }
                is Result.Error -> {
                    _state.value = VoiceCaptureUiState.Failed(result.error.message)
                }
            }
        }
    }

    // Inline edit in the review list — replace one draft's name.
    // No-op unless we're actually in Reviewing state (state machine guard).
    fun editDraft(index: Int, newName: String) {
        val current = _state.value as? VoiceCaptureUiState.Reviewing ?: return
        val updated = current.drafts.toMutableList().apply {
            this[index] = ItemDraft(newName)
        }
        _state.value = VoiceCaptureUiState.Reviewing(updated)
    }

    fun removeDraft(index: Int) {
        val current = _state.value as? VoiceCaptureUiState.Reviewing ?: return
        val updated = current.drafts.toMutableList().apply { removeAt(index) }
        _state.value = VoiceCaptureUiState.Reviewing(updated)
    }

    // Persistence is the caller's responsibility — the screen passes a lambda
    // that wraps shoppingListRepository.createListWithItems(...). The lambda
    // returns Result<String> where the String is the new list id (used to
    // navigate). This module doesn't know that — to it, it's just an opaque token.
    fun confirm(persist: suspend (List<ItemDraft>) -> Result<String>) {
        viewModelScope.launch {
            val current = _state.value as? VoiceCaptureUiState.Reviewing ?: return@launch
            _state.value = VoiceCaptureUiState.Saving
            when (val result = persist(current.drafts)) {
                is Result.Success -> {
                    _state.value = VoiceCaptureUiState.Done
                    _navigateToList.emit(result.data)
                }
                is Result.Error -> {
                    _state.value = VoiceCaptureUiState.Failed(result.error.message)
                }
            }
        }
    }

    // User aborts mid-flow — drop the audio file and reset.
    // Safe to call from any state; AudioRecorder.cancel() is idempotent.
    fun cancel() {
        recorder.cancel()
        _state.value = VoiceCaptureUiState.Idle
    }
}
