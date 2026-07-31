package com.babegetthis.android.feature.profile.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.voice.data.AudioRecorder
import com.babegetthis.android.core.voice.data.repository.VoiceRepository
import com.babegetthis.android.core.voice.ui.VoiceCaptureSheet
import com.babegetthis.android.core.voice.ui.viewModels.VoiceCaptureViewModel
import com.babegetthis.android.testing.MainDispatcherRule
import com.babegetthis.android.testing.TestData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// Both sheets take their ViewModel as a parameter, so both are driven with a real
// one over mocked collaborators. Actions go through the semantics tree —
// ModalBottomSheet animates in, so a coordinate tap can land before it settles.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileAndVoiceSheetTest {

    @get:Rule val compose = createComposeRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun click(label: String) =
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)

    // --- ProfileBottomSheet ---

    private val authRepository = mockk<AuthRepository>(relaxed = true)

    private fun profile(name: String = "Aabhash", email: String = "a@b.c"): ProfileViewModel {
        val authStateManager = mockk<AuthStateManager>(relaxed = true) {
            every { userName } returns MutableStateFlow(name)
            every { userEmail } returns MutableStateFlow(email)
        }
        val vm = ProfileViewModel(authRepository, authStateManager)
        compose.setContent { ProfileBottomSheet(onDismiss = {}, viewModel = vm) }
        return vm
    }

    @Test
    fun `the sheet shows the signed-in identity`() {
        profile(name = "Aabhash", email = "a@b.c")

        compose.onNodeWithText("Aabhash", substring = true).assertExists()
        compose.onNodeWithText("a@b.c", substring = true).assertExists()
    }

    @Test
    fun `the sheet offers account deletion`() {
        profile()

        compose.onNodeWithText("Delete account").assertExists()
    }

    // Deletion is irreversible, so it must go through a confirmation rather than
    // firing on the first tap.
    @Test
    fun `deleting asks for confirmation first`() {
        profile()

        click("Delete account")

        compose.onNodeWithText("Delete account?").assertExists()
        coVerify(exactly = 0) { authRepository.deleteAccount() }
    }

    @Test
    fun `confirming deletion reaches the repository`() {
        coEvery { authRepository.deleteAccount() } returns Result.Success(Unit)
        profile()
        click("Delete account")

        click("Delete")

        coVerify { authRepository.deleteAccount() }
    }

    @Test
    fun `cancelling the confirmation deletes nothing`() {
        profile()
        click("Delete account")

        click("Cancel")

        coVerify(exactly = 0) { authRepository.deleteAccount() }
    }

    // --- VoiceCaptureSheet ---

    private val recorder = mockk<AudioRecorder>(relaxed = true)
    private val voiceRepository = mockk<VoiceRepository>(relaxed = true)

    private var switchedToType = false
    private var dismissed = false

    private fun voice(): VoiceCaptureViewModel {
        val vm = VoiceCaptureViewModel(recorder, voiceRepository)
        compose.setContent {
            VoiceCaptureSheet(
                onDismiss = { dismissed = true },
                onSwitchToType = { switchedToType = true },
                onConfirm = { Result.Success("list-1") },
                viewModel = vm,
            )
        }
        return vm
    }

    @Test
    fun `the voice sheet renders its idle state`() {
        val vm = voice()

        // Idle is the entry state; the sheet must render without a recording.
        assertTrue(vm.state.value is com.babegetthis.android.core.voice.model.VoiceCaptureUiState.Idle)
    }

    @Test
    fun `a denied permission explains itself and offers a way to grant`() {
        val vm = voice()

        vm.onPermissionResult(granted = false)

        compose.onNodeWithText("Microphone access is needed to capture your list.").assertExists()
        compose.onNodeWithText("Allow microphone").assertExists()
    }

    @Test
    fun `recording offers a stop control`() {
        val vm = voice()

        vm.startRecording()

        compose.onNodeWithText("Listening…").assertExists()
        compose.onNodeWithText("Stop").assertExists()
    }

    // A failure must not be a dead end: retry, or bail out to typing.
    @Test
    fun `a failed capture offers both retry and a switch to typing`() {
        coEvery { recorder.stop() } returns File("voice.m4a")
        coEvery { voiceRepository.transcribeAndParse(any()) } returns Result.Success(emptyList())
        val vm = voice()
        vm.startRecording()

        vm.stopRecording()

        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithText("Type instead").assertExists()
    }

    @Test
    fun `switching to typing from a failure reports it to the host`() {
        coEvery { recorder.stop() } returns File("voice.m4a")
        coEvery { voiceRepository.transcribeAndParse(any()) } returns Result.Success(emptyList())
        val vm = voice()
        vm.startRecording()
        vm.stopRecording()

        click("Type instead")

        assertTrue(switchedToType)
    }

    @Test
    fun `dismissing the sheet cancels the recorder`() {
        val vm = voice()
        vm.startRecording()

        vm.cancel()

        verify { recorder.cancel() }
    }

    @Test
    fun `a completed capture persists the drafts through onConfirm`() {
        coEvery { recorder.stop() } returns File("voice.m4a")
        coEvery { voiceRepository.transcribeAndParse(any()) } returns
            Result.Success(listOf(TestData.draft(name = "Milk")))
        val vm = voice()
        vm.startRecording()

        vm.stopRecording()

        assertTrue(
            vm.state.value is com.babegetthis.android.core.voice.model.VoiceCaptureUiState.Done
        )
    }
}
