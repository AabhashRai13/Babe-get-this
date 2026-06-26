package com.babegetthis.android.core.voice.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.voice.model.ItemDraft
import com.babegetthis.android.core.voice.model.VoiceCaptureUiState
import com.babegetthis.android.core.voice.ui.viewModels.VoiceCaptureViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Modal bottom sheet that runs the whole voice-capture flow.
//
// The host (Lists screen) opens this sheet, hands it a `persist` lambda that
// knows how to name + create the list, and listens to viewModel.navigateToList
// for the new list id. The sheet only handles UI + permission — it does not
// know what gets persisted. There is no review step: once transcription
// returns items, the list is created and the host navigates into it.
//
// Flutter analogue: showModalBottomSheet(...) with a StatefulWidget inside;
// here `state` drives a `when` block instead of `setState` calls.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCaptureSheet(
    onDismiss: () -> Unit,
    // "Type instead" on the Failed state: close this sheet AND open the manual
    // type flow. Without this the sheet would just vanish, leaving the user with
    // nothing — which reads as a dead button.
    onSwitchToType: () -> Unit,
    onConfirm: suspend (drafts: List<ItemDraft>) -> Result<String>,
    viewModel: VoiceCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // Permission launcher — fires the Android system dialog and reports back.
    // On grant we immediately start recording so the user doesn't land on an
    // empty Idle screen wondering what to do.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.onPermissionResult(granted)
            if (granted) viewModel.startRecording()
        }
    )

    // Auto-start: the moment the sheet opens, seed the VM with the persist
    // lambda, then either begin recording (if mic permission is already
    // granted) or ask for it. LaunchedEffect(Unit) runs exactly once for the
    // lifetime of this composable.
    LaunchedEffect(Unit) {
        viewModel.setPersist(onConfirm)
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) viewModel.startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Auto-dismiss when the flow completes successfully. Navigation into the new
    // list is driven by ShoppingListViewModel.navigateToList (emitted when the
    // persist lambda creates the list), which the host collects — not by this VM.
    LaunchedEffect(state) {
        if (state is VoiceCaptureUiState.Done) {
            onDismiss()
            // This VM is scoped to the Lists screen, so it survives the
            // navigation into the freshly-created list and would otherwise stay
            // stuck in Done. Reset to Idle now (cancel is safe post-success) so
            // reopening the sheet records immediately — without this, the stale
            // Done auto-dismisses the first reopen ("tap mic, nothing happens,
            // tap again, works").
            viewModel.cancel()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.cancel()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is VoiceCaptureUiState.Idle -> IdleMode()
                is VoiceCaptureUiState.NeedsPermission -> NeedsPermissionMode(
                    onAllow = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
                is VoiceCaptureUiState.Recording -> RecordingMode(
                    onStop = { viewModel.stopRecording() },
                )
                is VoiceCaptureUiState.Transcribing -> TranscribingMode()
                is VoiceCaptureUiState.Saving -> LoadingMode("Saving your list…")
                is VoiceCaptureUiState.Done -> LoadingMode("Done!")
                is VoiceCaptureUiState.Failed -> FailedMode(
                    message = s.message,
                    onRetry = { viewModel.startRecording() },
                    onTypeInstead = {
                        viewModel.cancel()
                        onSwitchToType()
                    },
                )
            }
        }
    }
}

// Placeholder shown for the split-second before LaunchedEffect kicks off the
// first action. Render a small spinner so the sheet doesn't flash empty.
@Composable
private fun IdleMode() {
    Spacer(Modifier.height(24.dp))
    CircularProgressIndicator()
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun NeedsPermissionMode(onAllow: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Microphone access is needed to capture your list.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text("Allow microphone")
    }
}

// Recording mode — a big stop button and a running mm:ss timer. The timer is
// kept UI-local (not in the ViewModel) so we don't spam state updates every
// 100ms. The VM only cares that we're recording, not for how long.
@Composable
private fun RecordingMode(onStop: () -> Unit) {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (isActive) {
            elapsedMs = System.currentTimeMillis() - start
            delay(200)
        }
    }
    val totalSec = elapsedMs / 1000
    val mm = totalSec / 60
    val ss = totalSec % 60
    val timer = "%d:%02d".format(mm, ss)

    Spacer(Modifier.height(8.dp))
    // Big colored disc = "recording in progress" without needing a mic glyph
    // (Icons.Default.Mic lives in material-icons-extended, not in core).
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = timer,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Listening…",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Icon(Icons.Default.Close, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Stop")
    }
}

// Transcribing state: the decorative waveform plus a single line of reassurance
// that advances through a short sequence on a timer and HOLDS on the last line.
// We never loop back to the first line — cycling reads as "stuck", holding on
// "Almost there…" reads as "patient". The lines are warmth, not real stages
// (the backend exposes no progress), and the copy is placeholder for now.
@Composable
private fun TranscribingMode() {
    val lines = listOf(
        "Listening to your list…",
        "Sorting your items…",
        "Almost there…",
    )
    var lineIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (lineIndex < lines.lastIndex) {
            delay(2500)
            lineIndex++
        }
    }

    Spacer(Modifier.height(20.dp))
    TranscribingWaveform()
    Spacer(Modifier.height(20.dp))
    // Crossfade so the line swaps smoothly instead of snapping.
    Crossfade(targetState = lines[lineIndex], label = "reassurance") { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun LoadingMode(label: String) {
    Spacer(Modifier.height(16.dp))
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun FailedMode(
    message: String,
    onRetry: () -> Unit,
    onTypeInstead: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text("Try again")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onTypeInstead, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text("Type instead")
    }
}
