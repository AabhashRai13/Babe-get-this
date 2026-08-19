package com.babegetthis.android.core.voice.model

// One-of-many state holder for the voice-capture flow.
// `sealed interface` = exhaustive when-branches at the call site; the compiler
// will yell if you forget to handle a state. Like a Dart `sealed class` you'd
// switch over with no `default` clause.
sealed interface VoiceCaptureUiState {
    data object Idle : VoiceCaptureUiState
    data object NeedsPermission : VoiceCaptureUiState
    data class Recording(val elapsedMs: Long = 0) : VoiceCaptureUiState
    data object Transcribing : VoiceCaptureUiState
    data object Saving : VoiceCaptureUiState
    data object Done : VoiceCaptureUiState
    data class Failed(val message: String) : VoiceCaptureUiState
}
