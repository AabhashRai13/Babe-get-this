package com.babegetthis.android.feature.shoppinglist.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babegetthis.android.R
import com.babegetthis.android.core.ui.modifier.clearFocusOnTap

@Composable
fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    // Edit mode: when non-null, pre-fills with current name and changes button text
    currentName: String? = null,
    onRename: (String) -> Unit = {},
) {
    val isEditMode = currentName != null
    var listName by remember { mutableStateOf(currentName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Tap any blank spot in the dialog to close the keyboard.
        modifier = Modifier.clearFocusOnTap(),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (isEditMode) stringResource(R.string.shopping_list_edit_title)
                       else stringResource(R.string.shopping_list_create_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            OutlinedTextField(
                value = listName,
                onValueChange = { if (it.length <= 40) listName = it },
                label = { Text(stringResource(R.string.shopping_list_name_hint)) },
                placeholder = { Text("e.g. Weekly Groceries") },
                supportingText = if (listName.length >= 40) {
                    { Text("${listName.length}/40") }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (listName.isNotBlank()) {
                        if (isEditMode) onRename(listName.trim()) else onCreate(listName.trim())
                    }
                },
                enabled = listName.isNotBlank(),
            ) {
                Text(
                    if (isEditMode) stringResource(R.string.save)
                    else stringResource(R.string.create)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
