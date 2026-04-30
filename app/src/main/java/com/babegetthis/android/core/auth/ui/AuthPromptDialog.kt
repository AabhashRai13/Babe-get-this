package com.babegetthis.android.core.auth.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Shown when an unauthenticated user taps the Share button.
// Prompts them to sign in or create an account — this is the main
// entry point to auth now that the login wall is removed.
// Like a Flutter showDialog() with CupertinoAlertDialog.

@Composable
fun AuthPromptDialog(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sign in to share",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Create an account to share lists with your partner and sync across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onLogin()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = "Sign in",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onRegister()
                },
            ) {
                Text(
                    text = "Create account",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}
