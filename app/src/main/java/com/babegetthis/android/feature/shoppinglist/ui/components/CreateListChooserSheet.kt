package com.babegetthis.android.feature.shoppinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListChooserSheet(
    onDismiss: () -> Unit,
    onPickType: () -> Unit,
    onPickVoice: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        // M3 extra-large corner token for sheets.
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChooserTile(
                icon = Icons.Outlined.Edit,
                label = "Type",
                // Secondary tone keeps "Type" present but lets the marquee
                // Voice tile draw the eye.
                badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                onBadgeColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onPickType,
                modifier = Modifier.weight(1f),
            )
            ChooserTile(
                icon = Icons.Filled.Mic,
                label = "Voice",
                // Primary tone — voice is the headline create flow.
                badgeColor = MaterialTheme.colorScheme.primaryContainer,
                onBadgeColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onPickVoice,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// A single create-method tile: a tonal card holding a circular colored icon
// badge above its label. Surface(onClick) gives the proper M3 ripple/state
// layer for free, unlike a plain clickable Box.
@Composable
private fun ChooserTile(
    icon: ImageVector,
    label: String,
    badgeColor: Color,
    onBadgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        // Neutral container so the colored badges pop against it.
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Circular tinted badge — the visual anchor of the tile.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(28.dp),
                    tint = onBadgeColor,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
