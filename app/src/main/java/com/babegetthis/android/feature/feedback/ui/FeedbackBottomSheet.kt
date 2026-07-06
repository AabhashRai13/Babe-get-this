package com.babegetthis.android.feature.feedback.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.core.ui.modifier.clearFocusOnTap

// Feedback form in a modal bottom sheet, opened from the profile sheet — so
// it's only reachable while logged in (the Supabase table's RLS policy
// enforces the same rule server-side).
//
// Three free-text answers are optional; the one required answer is the
// yes/no "would you keep using it?" — that's the number we actually need.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackBottomSheet(
    onDismiss: () -> Unit,
    onToast: (String) -> Unit = {},
    viewModel: FeedbackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Forward toast events to whoever owns the SnackbarHost, same pattern as
    // ProfileBottomSheet.
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            onToast(message)
        }
    }

    // Close the sheet once the submit lands. Errors keep it open (with the
    // typed answers intact) so the user can retry.
    LaunchedEffect(Unit) {
        viewModel.dismissEvent.collect {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                // Scrollable because with the keyboard up, four fields don't fit.
                .verticalScroll(rememberScrollState())
                // Tap any blank spot in the sheet to close the keyboard.
                .clearFocusOnTap(),
        ) {
            Text(
                text = "Share your feedback",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Help us make the app better for you two.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            )

            OutlinedTextField(
                value = uiState.liked,
                onValueChange = { viewModel.onLikedChanged(it) },
                label = { Text("What did you like?") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.disliked,
                onValueChange = { viewModel.onDislikedChanged(it) },
                label = { Text("What didn't you like?") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Would you keep using the app?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.wouldUseApp == true,
                    onClick = { viewModel.onWouldUseAppChanged(true) },
                    label = { Text("Yes") },
                )
                FilterChip(
                    selected = uiState.wouldUseApp == false,
                    onClick = { viewModel.onWouldUseAppChanged(false) },
                    label = { Text("No") },
                )
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.improvements,
                onValueChange = { viewModel.onImprovementsChanged(it) },
                label = { Text("What should we improve?") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.submit() },
                enabled = uiState.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.CenterVertically),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = "Send feedback",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
