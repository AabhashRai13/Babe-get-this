package com.babegetthis.android.feature.gotthis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babegetthis.android.R
import com.babegetthis.android.feature.gotthis.data.UsualItem
import com.babegetthis.android.feature.gotthis.ui.viewModels.UsualsState

/** "Usually bought around now": tap a chip to add the item to this list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsualsRow(
    state: UsualsState,
    onChip: (UsualItem) -> Unit,
    onExpand: () -> Unit,
    onNotNow: () -> Unit,
    onHeadsUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().testTag("usuals_row")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.got_this_usuals_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNotNow) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.got_this_usuals_not_now),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.chips.forEach { item ->
                AssistChip(
                    onClick = { onChip(item) },
                    label = { Text("+ ${item.name}") },
                    modifier = Modifier.testTag("usual_chip"),
                )
            }
            if (state.more > 0) {
                AssistChip(
                    onClick = onExpand,
                    label = { Text(stringResource(R.string.got_this_usuals_more, state.more)) },
                    modifier = Modifier.testTag("usuals_more"),
                )
            }
        }
        if (state.offerHeadsUp) {
            TextButton(onClick = onHeadsUp) {
                Text(stringResource(R.string.got_this_heads_up))
            }
        }
    }
}
