package com.babegetthis.android.feature.shoppinglist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class TabPill(
    val label: String,
    val icon: ImageVector,
)

// Pill-style tab row — clean alternative to PrimaryTabRow's bottom divider.
// Selected tab gets a tinted pill background + bold text + primary color.
// Unselected tabs have transparent background with muted text.
// Touch ripple is bounded to the pill shape for a polished feel.
@Composable
internal fun TabPillRow(
    tabs: List<TabPill>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex

            // M3: selected state uses the primary role for high emphasis,
            // animated so the change reads as a transition not a swap.
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    Color.Transparent,
                animationSpec = tween(durationMillis = 200),
                label = "tabBackground",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = 200),
                label = "tabContent",
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        ),
                        onClick = { onTabSelected(index) },
                    ),
                shape = CircleShape,
                color = backgroundColor,
                shadowElevation = if (isSelected) 2.dp else 0.dp,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
