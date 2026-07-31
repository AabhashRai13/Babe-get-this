package com.babegetthis.android.core.voice.ui.viewModels

import app.cash.turbine.test
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.voice.data.AudioRecorder
import com.babegetthis.android.core.voice.data.repository.VoiceRepository
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.core.voice.model.VoiceCaptureUiState
import com.babegetthis.android.testing.MainDispatcherRule
import com.babegetthis.android.testing.TestData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

// AudioRecorder is mocked directly rather than hidden behind a new interface.
// Task 10.3 called for extracting one, but MockK handles the final class without
// ever running its constructor — so no MediaRecorder is touched and no
// production change is needed. Same call as dropping the hand-written fakes in
// task 1.9: the seam only earns its keep if something actually needs it.
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCaptureViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val recorder = mockk<AudioRecorder>(relaxed = true)
    private val voiceRepository = mockk<VoiceRepository>(relaxed = true)
    private val audioFile = File("voice-test.m4a")

    private fun viewModel() = VoiceCaptureViewModel(recorder, voiceRepository)

    private val drafts = listOf(TestData.draft(name = "Milk"), TestData.draft(name = "Eggs"))

    // --- permission gate ---

    @Test
    fun `starts idle`() = runTest {
        assertEquals(VoiceCaptureUiState.Idle, viewModel().state.value)
    }

    @Test
    fun `a denied permission parks in NeedsPermission`() = runTest {
        val vm = viewModel()

        vm.onPermissionResult(granted = false)

        assertEquals(VoiceCaptureUiState.NeedsPermission, vm.state.value)
    }

    @Test
    fun `a granted permission returns to idle`() = runTest {
        val vm = viewModel()
        vm.onPermissionResult(granted = false)

        vm.onPermissionResult(granted = true)

        assertEquals(VoiceCaptureUiState.Idle, vm.state.value)
    }

    // --- recording ---

    @Test
    fun `startRecording opens the mic then reports Recording`() = runTest {
        val vm = viewModel()

        vm.startRecording()

        coVerify { recorder.start() }
        assertTrue(vm.state.value is VoiceCaptureUiState.Recording)
    }

    // Recording is only announced once start() RETURNS — it plays a ~1s cue tone
    // before opening the mic, and flipping earlier ran the timer ahead of the real
    // capture and swallowed words spoken over the tone.
    @Test
    fun `Recording is not announced while the cue tone is still playing`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { recorder.start() } coAnswers { gate.await() }
        val vm = viewModel()

        vm.startRecording()
        assertEquals(VoiceCaptureUiState.Idle, vm.state.value)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value is VoiceCaptureUiState.Recording)
    }

    // Regression for a task 1.12 Critical: MediaRecorder.prepare() throws
    // IOException when another app holds the mic, and start() throws
    // IllegalStateException. Uncaught inside viewModelScope.launch those crashed
    // the app rather than surfacing anything.
    @Test
    fun `a mic that will not open lands in Failed instead of crashing`() = runTest {
        coEvery { recorder.start() } throws IOException("mic busy")
        val vm = viewModel()

        vm.startRecording()

        assertTrue(vm.state.value is VoiceCaptureUiState.Failed)
    }

    @Test
    fun `a failed start releases the recorder`() = runTest {
        coEvery { recorder.start() } throws IllegalStateException("bad state")
        val vm = viewModel()

        vm.startRecording()

        verify { recorder.cancel() }
    }

    @Test
    fun `the failure message does not leak the exception text`() = runTest {
        coEvery { recorder.start() } throws IOException("/data/user/0/cache/voice.m4a busy")
        val vm = viewModel()

        vm.startRecording()

        val message = (vm.state.value as VoiceCaptureUiState.Failed).message
        assertTrue("leaked internals: $message", !message.contains("/data/"))
    }

    // --- stop / transcribe ---

    @Test
    fun `stopRecording transcribes and persists`() = runTest {
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(audioFile) } returns Result.Success(drafts)
        val vm = viewModel()
        vm.setPersist { Result.Success("list-1") }
        vm.startRecording()

        vm.stopRecording()

        assertEquals(VoiceCaptureUiState.Done, vm.state.value)
    }

    @Test
    fun `stopRecording waits for an in-flight start before stopping`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { recorder.start() } coAnswers { gate.await() }
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(any()) } returns Result.Success(drafts)
        val vm = viewModel()
        vm.setPersist { Result.Success("list-1") }
        vm.startRecording()

        vm.stopRecording()
        // start() is still parked, so the recorder must not have been stopped.
        coVerify(exactly = 0) { recorder.stop() }

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { recorder.stop() }
    }

    @Test
    fun `nothing understood reports a retryable failure rather than an empty list`() = runTest {
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(any()) } returns Result.Success(emptyList())
        var persisted = false
        val vm = viewModel()
        vm.setPersist { persisted = true; Result.Success("list-1") }
        vm.startRecording()

        vm.stopRecording()

        assertTrue(vm.state.value is VoiceCaptureUiState.Failed)
        assertTrue("must never create an empty list", !persisted)
    }

    @Test
    fun `a transcription error surfaces its message`() = runTest {
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(any()) } returns
            Result.Error(AppError.NetworkError())
        val vm = viewModel()
        vm.startRecording()

        vm.stopRecording()

        assertEquals(
            VoiceCaptureUiState.Failed(AppError.NetworkError().message),
            vm.state.value,
        )
    }

    // Regression for a task 1.12 Critical: stop() throws the same way start()
    // does, and outputFile is force-unwrapped inside it.
    @Test
    fun `a recorder that fails to stop lands in Failed instead of crashing`() = runTest {
        coEvery { recorder.stop() } throws IllegalStateException("nothing captured")
        val vm = viewModel()
        vm.startRecording()

        vm.stopRecording()

        assertTrue(vm.state.value is VoiceCaptureUiState.Failed)
    }

    // Regression for a task 1.12 High: only cancel() ever deleted the recording,
    // so every SUCCESSFUL capture left an .m4a of the user's voice in cacheDir.
    @Test
    fun `the recording is deleted after a successful upload`() = runTest {
        val file = mockk<File>(relaxed = true)
        coEvery { recorder.stop() } returns file
        coEvery { voiceRepository.transcribeAndParse(any()) } returns Result.Success(drafts)
        val vm = viewModel()
        vm.setPersist { Result.Success("list-1") }
        vm.startRecording()

        vm.stopRecording()

        verify { file.delete() }
    }

    @Test
    fun `the recording is deleted even when transcription fails`() = runTest {
        val file = mockk<File>(relaxed = true)
        coEvery { recorder.stop() } returns file
        coEvery { voiceRepository.transcribeAndParse(any()) } returns
            Result.Error(AppError.ServerError(500))
        val vm = viewModel()
        vm.startRecording()

        vm.stopRecording()

        verify { file.delete() }
    }

    // --- persist ---

    @Test
    fun `the persist lambda receives the parsed drafts`() = runTest {
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(any()) } returns Result.Success(drafts)
        var received: List<ItemDraft>? = null
        val vm = viewModel()
        vm.setPersist { received = it; Result.Success("list-1") }
        vm.startRecording()

        vm.stopRecording()

        assertEquals(drafts, received)
    }

    @Test
    fun `a persist failure surfaces its message`() = runTest {
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(any()) } returns Result.Success(drafts)
        val vm = viewModel()
        vm.setPersist { Result.Error(AppError.DatabaseError()) }
        vm.startRecording()

        vm.stopRecording()

        assertEquals(
            VoiceCaptureUiState.Failed(AppError.DatabaseError().message),
            vm.state.value,
        )
    }

    // The sheet seeds the lambda on open, so this should be unreachable — it
    // fails loudly rather than silently dropping what the user just dictated.
    @Test
    fun `no persist lambda fails loudly rather than dropping the list`() = runTest {
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(any()) } returns Result.Success(drafts)
        val vm = viewModel()

        vm.startRecording()
        vm.stopRecording()

        assertTrue(vm.state.value is VoiceCaptureUiState.Failed)
    }

    @Test
    fun `Saving is announced before the persist completes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(any()) } returns Result.Success(drafts)
        val vm = viewModel()
        vm.setPersist { gate.await(); Result.Success("list-1") }
        vm.startRecording()

        vm.stopRecording()
        assertEquals(VoiceCaptureUiState.Saving, vm.state.value)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(VoiceCaptureUiState.Done, vm.state.value)
    }

    // --- cancel ---

    @Test
    fun `cancel resets to idle and releases the recorder`() = runTest {
        val vm = viewModel()
        vm.startRecording()

        vm.cancel()

        assertEquals(VoiceCaptureUiState.Idle, vm.state.value)
        verify { recorder.cancel() }
    }

    // Dismissing mid-flight must mean nothing lands.
    @Test
    fun `cancel aborts an in-flight transcription before it can persist`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(any()) } coAnswers {
            gate.await()
            Result.Success(drafts)
        }
        var persisted = false
        val vm = viewModel()
        vm.setPersist { persisted = true; Result.Success("list-1") }
        vm.startRecording()
        vm.stopRecording()

        vm.cancel()
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertTrue("a dismissed sheet must not persist anything", !persisted)
        assertEquals(VoiceCaptureUiState.Idle, vm.state.value)
    }

    @Test
    fun `cancel is safe from idle`() = runTest {
        val vm = viewModel()

        vm.cancel()

        assertEquals(VoiceCaptureUiState.Idle, vm.state.value)
    }

    // Pinned rather than endorsed: cancel() resets unconditionally, so it drives
    // the machine back out of NeedsPermission too. Fine in practice — it is only
    // reached when the sheet is closing — but it means the state machine has no
    // guard against leaving a terminal state.
    @Test
    fun `cancel also clears NeedsPermission`() = runTest {
        val vm = viewModel()
        vm.onPermissionResult(granted = false)

        vm.cancel()

        assertEquals(VoiceCaptureUiState.Idle, vm.state.value)
    }

    // state is a StateFlow, which conflates: on an unconfined dispatcher the whole
    // stop -> transcribe -> persist chain completes before a collector resumes, so
    // a naive Turbine walk sees only Done and the intermediate states look like
    // they never happened. Gating the transcribe call is what makes the ordering
    // genuinely observable rather than a timing accident.
    @Test
    fun `state transitions are observable in order`() = runTest {
        val transcribeGate = CompletableDeferred<Unit>()
        val persistGate = CompletableDeferred<Unit>()
        coEvery { recorder.stop() } returns audioFile
        coEvery { voiceRepository.transcribeAndParse(any()) } coAnswers {
            transcribeGate.await()
            Result.Success(drafts)
        }
        val vm = viewModel()
        vm.setPersist { persistGate.await(); Result.Success("list-1") }

        vm.startRecording()
        assertTrue(vm.state.value is VoiceCaptureUiState.Recording)

        vm.stopRecording()
        assertEquals(VoiceCaptureUiState.Transcribing, vm.state.value)

        transcribeGate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(VoiceCaptureUiState.Saving, vm.state.value)

        persistGate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(VoiceCaptureUiState.Done, vm.state.value)
    }
}
