package com.babegetthis.android.core.voice.ui.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.telemetry.AnalyticsRepository
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.VoiceFailureReason
import com.babegetthis.android.core.voice.data.AudioRecorder
import com.babegetthis.android.core.voice.data.repository.VoiceRepository
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.core.voice.model.VoiceCaptureUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Orchestrates the whole voice-capture flow:
//   record → transcribe → persist → navigate.
//
// There is no review step: as soon as transcription returns items, the list is
// created and we navigate into it. The user edits items in the list screen.
//
// On purpose, this ViewModel only knows about AudioRecorder + VoiceRepository.
// It does NOT depend on ShoppingListRepository — the sheet seeds a `persist`
// lambda (via setPersist) so this module stays reusable (a future "add items to
// an existing list" feature could reuse it with a different persist lambda).
@HiltViewModel
class VoiceCaptureViewModel @Inject constructor(
    private val recorder: AudioRecorder,
    private val voiceRepository: VoiceRepository,
    private val analytics: AnalyticsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceCaptureUiState>(VoiceCaptureUiState.Idle)
    val state: StateFlow<VoiceCaptureUiState> = _state.asStateFlow()

    // How to persist the parsed drafts → returns the new list id. Seeded by the
    // sheet on open so this module stays agnostic about naming + storage (those
    // are shopping-list concerns). Must be set before transcription completes.
    private var persist: (suspend (drafts: List<ItemDraft>) -> Result<String>)? = null

    // Tracks the in-flight transcribe→persist coroutine so cancel() can abort it.
    // Without this, closing the sheet mid-request lets the request finish in the
    // background and persist items the user thought they'd cancelled.
    private var transcribeJob: Job? = null

    // Tracks the in-flight start (cue tone + mic open). recorder.start() suspends
    // for ~1s playing the tone, and that window is human-tappable: stop must WAIT
    // for it (recorder/outputFile don't exist until it finishes), and cancel must
    // ABORT it (dismissing the sheet mid-tone must never open the mic afterwards).
    private var recordJob: Job? = null

    // When the mic actually opened, or null if we are not recording. Doubles as
    // the "is a recording in flight?" flag that tells a genuine user abort apart
    // from the cancel() the sheet fires to reset itself after a SUCCESSFUL
    // capture — without it, every successful voice list would also report an
    // abandonment and the funnel would read as ~100% drop-off at the last step.
    private var recordingStartedAt: Long? = null

    fun setPersist(block: suspend (drafts: List<ItemDraft>) -> Result<String>) {
        persist = block
    }

    // Top of the funnel. Called from the sheet's open effect rather than from
    // startRecording, because the gap between the two is exactly the permission
    // prompt — and users lost there are the ones worth counting.
    fun onSheetOpened() {
        analytics.track(AnalyticsEvent.VoiceSheetOpened)
    }

    // Called by the screen after the system permission dialog resolves.
    // If denied, we sit in NeedsPermission so the UI can show an explainer.
    fun onPermissionResult(granted: Boolean) {
        _state.value = if (granted) VoiceCaptureUiState.Idle else VoiceCaptureUiState.NeedsPermission
    }

    fun startRecording() {
        recordJob = viewModelScope.launch {
            // MediaRecorder throws for ordinary device conditions, not just bugs:
            // prepare() raises IOException when another app holds the mic (a call,
            // another recorder), start() raises IllegalStateException. Uncaught
            // inside viewModelScope.launch those crashed the app — the only place
            // in the codebase where a device-state failure wasn't turned into a
            // Result the UI could render. CancellationException is rethrown so
            // cancel() still aborts the cue tone rather than showing an error.
            try {
                // start() plays the cue tone BEFORE opening the mic, so we only flip
                // to Recording once it returns — i.e. when the mic is genuinely live.
                // Flipping earlier made the timer run ~1s ahead of the real capture
                // and swallowed words spoken during the tone. Until then the sheet
                // shows its Idle spinner, which reads as "getting ready".
                recorder.start()
                recordingStartedAt = System.currentTimeMillis()
                _state.value = VoiceCaptureUiState.Recording()
                analytics.track(AnalyticsEvent.VoiceRecordingStarted)
                // Elapsed-time tick intentionally omitted for v1 — add later if the
                // UI grows a timer/waveform. Recording state alone is enough today.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recorder.cancel()
                recordingStartedAt = null
                _state.value =
                    VoiceCaptureUiState.Failed("Couldn't start recording. Is the microphone in use?")
                // A device condition rather than a bug, so not a crash report —
                // but a high rate here means the feature is simply unusable for
                // some slice of devices, which nothing else would tell us.
                analytics.track(AnalyticsEvent.VoiceRecordingFailed)
            }
        }
    }

    fun stopRecording() {
        transcribeJob = viewModelScope.launch {
            _state.value = VoiceCaptureUiState.Transcribing
            try {
                // Wait for start() to finish (cue tone + mic open) before stopping.
                // Without this, tapping Stop during the tone hits a recorder that
                // doesn't exist yet and crashes. Stopping right after the mic opens
                // just yields an ~empty file, which the flow below already handles.
                recordJob?.join()
                // The recording is over; a cancel() from here on is the sheet
                // tidying up, not the user walking away mid-capture.
                recordingStartedAt = null
                val file = recorder.stop()
                try {
                    val startedAt = System.currentTimeMillis()
                    when (val result = voiceRepository.transcribeAndParse(file)) {
                        is Result.Success -> {
                            val latency = System.currentTimeMillis() - startedAt
                            // Empty result = nothing was understood. Guard BEFORE persisting
                            // so we never create an empty list.
                            if (result.data.isEmpty()) {
                                _state.value =
                                    VoiceCaptureUiState.Failed("Didn't catch any items. Try again?")
                                // Reported as a FAILURE, not a zero-item success.
                                // Technically the request worked; from the user's
                                // side the feature did nothing, and this is the
                                // failure they hit most.
                                analytics.track(
                                    AnalyticsEvent.VoiceTranscriptionFailed(
                                        reason = VoiceFailureReason.NothingHeard,
                                        latencyMillis = latency,
                                    ),
                                )
                            } else {
                                analytics.track(
                                    AnalyticsEvent.VoiceTranscriptionCompleted(
                                        latencyMillis = latency,
                                        itemCount = result.data.size,
                                    ),
                                )
                                persistDrafts(result.data)
                            }
                        }
                        is Result.Error -> {
                            _state.value = VoiceCaptureUiState.Failed(result.error.message)
                            analytics.track(
                                AnalyticsEvent.VoiceTranscriptionFailed(
                                    // The AppError type, never its message —
                                    // that string can carry server text.
                                    reason = VoiceFailureReason.from(result.error),
                                    latencyMillis = System.currentTimeMillis() - startedAt,
                                ),
                            )
                        }
                    }
                } finally {
                    // The recording is the user's voice and we are done with it —
                    // delete it whether the upload succeeded, failed, or was
                    // cancelled. Previously only cancel() ever deleted, so every
                    // SUCCESSFUL capture left an .m4a in cacheDir permanently.
                    file.delete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // stop() can throw the same way start() can (see above), and
                // outputFile is null if stop somehow ran without a completed start.
                recorder.cancel()
                _state.value = VoiceCaptureUiState.Failed("Couldn't finish that recording. Try again?")
            }
        }
    }

    // Auto-create the list from the parsed drafts and navigate into it — no
    // review step. The persist lambda (seeded by the sheet) names + stores the
    // list and returns its id.
    private suspend fun persistDrafts(drafts: List<ItemDraft>) {
        val persistFn = persist ?: run {
            // Should never happen — the sheet seeds this on open. Fail loudly
            // rather than silently dropping the user's spoken list.
            _state.value = VoiceCaptureUiState.Failed("Something went wrong saving your list.")
            return
        }
        _state.value = VoiceCaptureUiState.Saving
        when (val result = persistFn(drafts)) {
            is Result.Success -> {
                // Navigation into the new list is driven by the host via
                // ShoppingListViewModel.navigateToList (emitted inside the persist
                // lambda), so we only need to flip to Done here.
                _state.value = VoiceCaptureUiState.Done
                // The bottom of the funnel: items are in the database. Anything
                // between here and VoiceTranscriptionCompleted is a persist
                // failure, which is ours rather than the transcriber's.
                analytics.track(AnalyticsEvent.VoiceItemsSaved(itemCount = drafts.size))
            }
            is Result.Error -> {
                _state.value = VoiceCaptureUiState.Failed(result.error.message)
            }
        }
    }

    // User aborts mid-flow — drop the audio file and reset.
    // Safe to call from any state; AudioRecorder.cancel() is idempotent.
    fun cancel() {
        // Only a live recording counts as an abandonment. The sheet also calls
        // cancel() to reset itself out of Done after a successful capture, and
        // by then stopRecording has already cleared this.
        recordingStartedAt?.let { startedAt ->
            analytics.track(
                AnalyticsEvent.VoiceRecordingCancelled(
                    elapsedMillis = System.currentTimeMillis() - startedAt,
                ),
            )
            recordingStartedAt = null
        }
        // Abort a start() still playing the cue tone — cancellation releases the
        // MediaPlayer (invokeOnCancellation in AudioRecorder) and the coroutine
        // never reaches the mic-open step, so no orphaned recorder is left hot.
        recordJob?.cancel()
        recordJob = null
        // Abort any in-flight transcribe/persist so dismissing the sheet means
        // nothing lands. The DB insert is a single atomic transaction, so there's
        // no half-written-list risk if we cancel mid-save.
        transcribeJob?.cancel()
        transcribeJob = null
        recorder.cancel()
        _state.value = VoiceCaptureUiState.Idle
    }
}
