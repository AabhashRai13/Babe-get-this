package com.babegetthis.android.feature.shoppingitems.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.babegetthis.android.core.ui.TestTags
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem
import kotlinx.coroutines.delay

@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    onClick: () -> Unit,
    onTogglePickedUp: () -> Unit,
) {
    // --- Sub-animations for the pick-up moment ---
    // Following the checklist's "break it into sub-animations" pattern:
    //   1) card container colour eases instead of snapping
    //   2) checkbox surface colour eases
    //   3) check icon Crossfades in/out
    //   4) checkbox scales 1.0 → 1.15 → 1.0 on every TOGGLE (spring pop)
    //   5) text colour eases to/from the muted state
    val animSpec = tween<Color>(durationMillis = 220)
    val cardColor by animateColorAsState(
        targetValue = if (item.isPickedUp)
            MaterialTheme.colorScheme.surfaceContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = animSpec,
        label = "card-color",
    )
    val checkboxColor by animateColorAsState(
        targetValue = if (item.isPickedUp)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = animSpec,
        label = "checkbox-color",
    )
    val textColor by animateColorAsState(
        targetValue = if (item.isPickedUp)
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = animSpec,
        label = "text-color",
    )

    // Scale pop on the checkbox — driven by a target that we briefly bump
    // to 1.15f and let spring back. `hasInitialized` skips the pop on the
    // very first composition (e.g. when scrolling into view), so it only
    // plays on a real user toggle.
    var scaleTarget by remember(item.id) { mutableFloatStateOf(1f) }
    var hasInitialized by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.isPickedUp) {
        if (hasInitialized) {
            scaleTarget = 1.15f
            delay(120)
            scaleTarget = 1f
        }
        hasInitialized = true
    }
    val checkboxScale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkbox-scale",
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.itemCard(item.id))
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (item.isPickedUp) 0.dp else 2.dp,
        ),
        border = if (!item.isPickedUp) BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Circular checkbox — filled when picked up, outlined when active.
            // Colour eases via animateColorAsState; the check icon Crossfades
            // in; the whole circle pops on toggle via the scale modifier.
            Surface(
                onClick = onTogglePickedUp,
                shape = CircleShape,
                color = checkboxColor,
                border = if (!item.isPickedUp)
                    CardDefaults.outlinedCardBorder()
                else
                    null,
                modifier = Modifier
                    .size(30.dp)
                    .testTag(TestTags.itemCheckbox(item.id))
                    .scale(checkboxScale),
            ) {
                Crossfade(
                    targetState = item.isPickedUp,
                    animationSpec = tween(180),
                    label = "check-icon",
                ) { pickedUp ->
                    if (pickedUp) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isPickedUp) FontWeight.Normal else FontWeight.Medium,
                    textDecoration = if (item.isPickedUp) TextDecoration.LineThrough else TextDecoration.None,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!item.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = if (item.isPickedUp)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Chips for quantity and category — only on active items
                if (!item.isPickedUp && (item.quantity.isNotBlank() || item.categoryName != null)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.quantity.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                                ),
                            ) {
                                Text(
                                    text = "Qty: ${item.quantity}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                        }
                        if (item.categoryName != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                border = BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                                ),
                            ) {
                                Text(
                                    text = item.categoryName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
