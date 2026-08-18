package com.babegetthis.android.feature.shoppingitems.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.babegetthis.android.R

// Shows the list's static share code. No share-sheet here on purpose (see
// docs/product-decisions/001): the user texts the code with whatever app they
// already talk to their partner in.
@Composable
fun ShareCodeDialog(
    code: String,
    onDismiss: () -> Unit,
    // Copying IS the sharing act here — there is no share sheet, the user
    // pastes the code into whatever app they already talk in. So this is the
    // only observable point between "code exists" and "partner has it".
    onCodeCopied: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.share_live_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.displaySmall,
                    letterSpacing = MaterialTheme.typography.displaySmall.fontSize / 4,
                )
                Text(
                    text = stringResource(R.string.share_live_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            // Android 13+ shows its own "copied" overlay, so no snackbar here.
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                onCodeCopied()
            }) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(stringResource(R.string.share_live_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
    )
}
