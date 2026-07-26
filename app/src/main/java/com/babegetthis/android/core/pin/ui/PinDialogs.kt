package com.babegetthis.android.core.pin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babegetthis.android.R
import com.babegetthis.android.core.pin.data.PinResult
import kotlinx.coroutines.launch

// What a verify-existing-PIN prompt is for — drives title and confirm label.
enum class PinPromptPurpose(val titleRes: Int, val confirmRes: Int) {
    Unlock(R.string.pin_unlock_title, R.string.pin_unlock),
    Delete(R.string.pin_delete_title, R.string.pin_delete),
    Share(R.string.pin_share_title, R.string.pin_unlock),
    VerifyCurrent(R.string.pin_verify_current_title, R.string.pin_continue),
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(1)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        else -> "${s / 3600}h"
    }
}

@Composable
private fun pinErrorText(result: PinResult?): String? = when (result) {
    is PinResult.Wrong -> stringResource(R.string.pin_wrong, result.attemptsRemaining)
    is PinResult.LockedOut -> stringResource(R.string.pin_locked_out, formatDuration(result.remainingMs))
    else -> null
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

// Verify an existing PIN. Calls onVerified only on a correct entry.
@Composable
fun PinPromptDialog(
    purpose: PinPromptPurpose,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
    vm: PinPromptViewModel = hiltViewModel(),
) {
    var pin by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PinResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lockedOut = result is PinResult.LockedOut

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(purpose.titleRes)) },
        text = {
            Column {
                PinField(pin, { pin = it; result = null }, stringResource(R.string.pin_label), isError = result is PinResult.Wrong)
                pinErrorText(result)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValidPin(pin) && !busy && !lockedOut,
                onClick = {
                    scope.launch {
                        busy = true
                        val r = vm.verify(pin)
                        busy = false
                        if (r is PinResult.Success) onVerified() else { result = r; pin = "" }
                    }
                },
            ) { Text(stringResource(purpose.confirmRes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) } },
    )
}

// First-time PIN creation: enter -> confirm -> save recovery code.
@Composable
fun PinSetupDialog(
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    vm: PinPromptViewModel = hiltViewModel(),
) {
    var first by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val code = recoveryCode
    if (code != null) {
        RecoveryCodeStep(code = code, onAcknowledge = onComplete)
        return
    }

    AlertDialog(
        // Recovery step handles its own (blocked) dismissal; here plain cancel is fine.
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (confirming) R.string.pin_confirm_title else R.string.pin_create_title)) },
        text = {
            Column {
                Text(stringResource(if (confirming) R.string.pin_confirm_body else R.string.pin_create_body))
                if (confirming) {
                    PinField(confirm, { confirm = it; mismatch = false }, stringResource(R.string.pin_label), isError = mismatch)
                } else {
                    PinField(first, { first = it }, stringResource(R.string.pin_label))
                }
                if (mismatch) {
                    Text(stringResource(R.string.pin_mismatch), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            if (!confirming) {
                TextButton(enabled = isValidPin(first), onClick = { confirming = true }) {
                    Text(stringResource(R.string.pin_continue))
                }
            } else {
                TextButton(
                    enabled = isValidPin(confirm) && !busy,
                    onClick = {
                        if (confirm != first) {
                            mismatch = true; confirm = ""
                        } else {
                            scope.launch { busy = true; recoveryCode = vm.setupPin(first); busy = false }
                        }
                    },
                ) { Text(stringResource(R.string.pin_confirm)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) } },
    )
}

// Reset a forgotten PIN with the recovery code: code -> new PIN -> new code.
@Composable
fun RecoveryResetDialog(
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    vm: PinPromptViewModel = hiltViewModel(),
) {
    var code by remember { mutableStateOf("") }
    var codeVerified by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val issued = newCode
    if (issued != null) {
        RecoveryCodeStep(code = issued, onAcknowledge = onComplete)
        return
    }

    val wrongCodeMsg = stringResource(R.string.recovery_wrong)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (codeVerified) R.string.pin_new_title else R.string.recovery_enter_title)) },
        text = {
            Column {
                if (!codeVerified) {
                    Text(stringResource(R.string.recovery_enter_body))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; error = null },
                        label = { Text(stringResource(R.string.recovery_label)) },
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PinField(newPin, { newPin = it }, stringResource(R.string.pin_label))
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            if (!codeVerified) {
                TextButton(
                    enabled = code.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val r = vm.verifyRecovery(code)
                            busy = false
                            when (r) {
                                is PinResult.Success -> { codeVerified = true; error = null }
                                is PinResult.LockedOut -> error = formatLocked(r)
                                else -> error = wrongCodeMsg
                            }
                        }
                    },
                ) { Text(stringResource(R.string.pin_continue)) }
            } else {
                TextButton(
                    enabled = isValidPin(newPin) && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val (r, issuedCode) = vm.resetWithRecovery(code, newPin)
                            busy = false
                            when {
                                issuedCode != null -> newCode = issuedCode
                                r is PinResult.LockedOut -> error = formatLocked(r)
                                else -> error = wrongCodeMsg
                            }
                        }
                    },
                ) { Text(stringResource(R.string.pin_continue)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) } },
    )
}

private fun formatLocked(r: PinResult.LockedOut): String =
    "Too many attempts. Try again in ${formatDuration(r.remainingMs)}."

// Change the PIN: verify current -> enter new twice. Recovery code is left
// untouched (changePin does not regenerate it).
@Composable
fun ChangePinDialog(
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    vm: PinPromptViewModel = hiltViewModel(),
) {
    var current by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (!verified) R.string.pin_verify_current_title else R.string.pin_new_title)) },
        text = {
            Column {
                when {
                    !verified -> PinField(current, { current = it; error = null }, stringResource(R.string.pin_label), isError = error != null)
                    !confirming -> PinField(newPin, { newPin = it }, stringResource(R.string.pin_label))
                    else -> PinField(confirm, { confirm = it; mismatch = false }, stringResource(R.string.pin_label), isError = mismatch)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                if (mismatch) Text(stringResource(R.string.pin_mismatch), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && when {
                    !verified -> isValidPin(current)
                    !confirming -> isValidPin(newPin)
                    else -> isValidPin(confirm)
                },
                onClick = {
                    when {
                        !verified -> scope.launch {
                            busy = true
                            val r = vm.verify(current)
                            busy = false
                            when (r) {
                                is PinResult.Success -> verified = true
                                is PinResult.LockedOut -> error = formatLocked(r)
                                is PinResult.Wrong -> error = "Wrong PIN. ${r.attemptsRemaining} attempts left."
                            }
                        }
                        !confirming -> confirming = true
                        else -> {
                            if (confirm != newPin) { mismatch = true; confirm = "" }
                            else scope.launch {
                                busy = true
                                val r = vm.changePin(current, newPin)
                                busy = false
                                if (r is PinResult.Success) onComplete() else error = "Couldn't change PIN"
                            }
                        }
                    }
                },
            ) { Text(stringResource(R.string.pin_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) } },
    )
}

// Remove the PIN: verify current, warn about how many lists will unlock.
@Composable
fun RemovePinDialog(
    lockedCount: Int,
    onRemoved: () -> Unit,
    onDismiss: () -> Unit,
    vm: PinPromptViewModel = hiltViewModel(),
) {
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_pin_remove)) },
        text = {
            Column {
                if (lockedCount > 0) {
                    Text(stringResource(R.string.lock_remove_pin_warning, lockedCount), modifier = Modifier.padding(bottom = 12.dp))
                }
                PinField(current, { current = it; error = null }, stringResource(R.string.pin_label), isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValidPin(current) && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        val r = vm.removePin(current)
                        busy = false
                        when (r) {
                            is PinResult.Success -> onRemoved()
                            is PinResult.LockedOut -> error = formatLocked(r)
                            is PinResult.Wrong -> error = "Wrong PIN. ${r.attemptsRemaining} attempts left."
                        }
                    }
                },
            ) { Text(stringResource(R.string.pin_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) } },
    )
}

// Regenerate the recovery code: verify current PIN, then show the new code once.
@Composable
fun RegenerateRecoveryDialog(
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    vm: PinPromptViewModel = hiltViewModel(),
) {
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val issued = newCode
    if (issued != null) {
        RecoveryCodeStep(code = issued, onAcknowledge = onComplete)
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_verify_current_title)) },
        text = {
            Column {
                PinField(current, { current = it; error = null }, stringResource(R.string.pin_label), isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValidPin(current) && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        val r = vm.verify(current)
                        newCode = if (r is PinResult.Success) vm.regenerateRecoveryCode() else null
                        busy = false
                        if (r !is PinResult.Success) {
                            error = if (r is PinResult.LockedOut) formatLocked(r) else "Wrong PIN"
                        }
                    }
                },
            ) { Text(stringResource(R.string.pin_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) } },
    )
}

// Shown once. The only way out is acknowledging — outside/back dismissal is
// blocked so the code can't be skipped past by accident.
@Composable
private fun RecoveryCodeStep(code: String, onAcknowledge: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { /* blocked: must acknowledge */ },
        title = { Text(stringResource(R.string.recovery_title)) },
        text = {
            Column {
                Text(stringResource(R.string.recovery_body))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.recovery_copied))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) { Text(stringResource(R.string.recovery_acknowledge)) }
        },
    )
}
