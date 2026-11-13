package com.babegetthis.android.feature.gotthis.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.babegetthis.android.R

/** "Bought 3 days ago" under an unticked item; tapping it opens remove / stop-reminding actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoughtHintLine(daysAgo: Int, onRemove: () -> Unit, onMute: () -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val text = when (daysAgo) {
        0 -> stringResource(R.string.got_this_bought_today)
        1 -> stringResource(R.string.got_this_bought_yesterday)
        else -> pluralStringResource(R.plurals.got_this_bought_days_ago, daysAgo, daysAgo)
    }
    Box(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .testTag("bought_hint")
                .fillMaxWidth()
                .clickable { menuOpen = true }
                .minimumInteractiveComponentSize(),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.got_this_hint_remove)) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.got_this_hint_mute)) },
                onClick = {
                    menuOpen = false
                    onMute()
                },
            )
        }
    }
}
