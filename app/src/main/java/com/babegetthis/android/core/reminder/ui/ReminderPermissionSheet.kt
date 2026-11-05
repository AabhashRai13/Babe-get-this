package com.babegetthis.android.core.reminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babegetthis.android.R

/**
 * In-context ask for the notification permission, shown right after a tick that leaves the list
 * partly shopped. Dismissing the sheet (swipe or scrim tap) counts as "Not now".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPermissionSheet(onAccept: () -> Unit, onDecline: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDecline,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reminder_prompt_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.reminder_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reminder_prompt_accept))
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reminder_prompt_decline))
            }
        }
    }
}
