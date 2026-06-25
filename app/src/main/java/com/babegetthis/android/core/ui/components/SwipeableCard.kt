package com.babegetthis.android.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.babegetthis.android.core.ui.haptics.Haptic
import com.babegetthis.android.core.ui.haptics.rememberHaptic

// Reusable swipe wrapper for cards.
// Like Flutter's Dismissible widget — reveals colored backgrounds with icons on swipe.
//
// Usage:
//   SwipeableCard(
//       onSwipeLeft = { deleteItem() },     // red + trash icon
//       onSwipeRight = { togglePickedUp() }, // green + check icon (optional)
//   ) {
//       YourCardContent()
//   }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCard(
    onSwipeLeft: () -> Unit,
    onSwipeRight: (() -> Unit)? = null,
    leftIcon: ImageVector = Icons.Filled.Delete,
    rightIcon: ImageVector = Icons.Filled.Check,
    leftColor: Color = Color(0xFFE53935), // Red
    rightColor: Color = Color(0xFF43A047), // Green
    content: @Composable () -> Unit,
) {
    val haptic = rememberHaptic()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    // Destructive direction (delete) — firmer buzz.
                    haptic(Haptic.Medium)
                    onSwipeLeft()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (onSwipeRight != null) {
                        // Toggle direction (pick up / un-pick) — matches checkbox tap.
                        haptic(Haptic.Light)
                        onSwipeRight()
                        true
                    } else {
                        false // Don't allow right swipe if no action
                    }
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    // Reset the swipe state after action completes (so the card snaps back)
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = onSwipeRight != null,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            val backgroundColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> leftColor.copy(alpha = 0.9f)
                    SwipeToDismissBoxValue.StartToEnd -> rightColor.copy(alpha = 0.9f)
                    else -> Color.Transparent
                },
                label = "swipe-bg-color",
            )

            val icon = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> leftIcon
                SwipeToDismissBoxValue.StartToEnd -> rightIcon
                else -> leftIcon
            }

            val alignment = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment,
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        },
        content = { content() },
    )
}
