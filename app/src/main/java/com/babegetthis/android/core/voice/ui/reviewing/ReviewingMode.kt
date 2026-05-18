package com.babegetthis.android.core.voice.ui.reviewing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.babegetthis.android.core.ui.components.SwipeableCard
import com.babegetthis.android.core.voice.model.ItemDraft
import kotlinx.coroutines.delay

// Extracted from VoiceCaptureSheet so this screen — the most visually
// complex state of the voice flow — has room to grow without bloating
// the sheet orchestrator.
//
// Behavior is unchanged from the previous in-sheet version; UI redesign
// happens in subsequent steps.
@Composable
internal fun ReviewingMode(
    drafts: List<ItemDraft>,
    listName: String,
    onEditName: (String) -> Unit,
    onEdit: (Int, String) -> Unit,
    onEditQty: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    EditableTitle(value = listName, onValueChange = onEditName)
    Spacer(Modifier.height(16.dp))

    // LazyColumn so long lists scroll inside the sheet rather than push the
    // CTAs off-screen.
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(drafts) { index, draft ->
            // Stagger each row in 50ms after the previous one — sells the
            // "transcribed live" feel. LaunchedEffect(Unit) runs once per
            // composable instance; LazyColumn keys by position when no `key`
            // is given, so editing a row doesn't replay the animation and
            // deleting a row doesn't reset the survivors.
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 50L)
                visible = true
            }
            // Swipe-left to delete — matches the gesture used on shopping items
            // and shopping lists elsewhere. No right-swipe action because items
            // don't exist yet (nothing to mark as picked up at this stage).
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { it / 4 },
            ) {
                SwipeableCard(
                    onSwipeLeft = { onRemove(index) },
                ) {
                    ReviewItemRow(
                        name = draft.name,
                        quantity = draft.quantity,
                        onNameChange = { onEdit(index, it) },
                        onQtyChange = { onEditQty(index, it) },
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onConfirm,
        enabled = drafts.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text("Create list")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}

// Centered editable list-name title with a trailing pencil icon. Renders as
// flat (no visible border) to match the mockup; the pencil is decorative —
// the whole field is tappable to focus and edit.
@Composable
private fun EditableTitle(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        ),
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit list name",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
