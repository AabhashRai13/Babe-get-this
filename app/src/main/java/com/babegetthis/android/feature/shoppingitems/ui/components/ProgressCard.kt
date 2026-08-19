package com.babegetthis.android.feature.shoppingitems.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Progress card — shows how far along the shopping trip is.
// A visual focal point that gives the screen character.
//
// Celebration sub-animations when the list transitions to all-done:
//   1) Card pops in: scale snaps to 0.85f, springs back to 1.0f
//   2) Filled.CheckCircle appears next to "All done!", rotating from
//      -90° to 0° and fading in over 200ms
//   3) (Haptic.Success is handled at the screen level via the ViewModel
//       UiEvent.ListJustCompleted flow — see ShoppingItemsScreen.)
//
// All three skip on the initial composition (hasInitialized guard) so
// opening a list that's already complete doesn't replay the celebration.
@Composable
fun ProgressCard(
    totalItems: Int,
    completedCount: Int,
) {
    val progress = if (totalItems > 0) completedCount.toFloat() / totalItems else 0f
    val allDone = completedCount == totalItems && totalItems > 0

    // Animatable lets us snapTo (jump instantly) then animateTo, which is
    // how we get "card appears at 0.85 then springs up to 1.0" rather
    // than the smooth lerp animateFloatAsState would produce.
    val cardScale = remember { Animatable(1f) }
    // Icon scales 0 -> 1 with a bouncy spring so it overshoots naturally.
    // AnimatedVisibility handles the layout slot reveal + fade; graphicsLayer
    // scale adds the bouncy pop on top.
    val checkScale = remember { Animatable(if (allDone) 1f else 0f) }
    // One-shot 0 -> 1 -> 0 pulse for the container color flash.
    val pulseProgress = remember { Animatable(0f) }
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(allDone) {
        if (hasInitialized && allDone) {
            // Just transitioned into all-done — play the celebration.
            cardScale.snapTo(0.85f)
            checkScale.snapTo(0f)
            pulseProgress.snapTo(0f)

            launch {
                cardScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                )
            }
            launch {
                checkScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            launch {
                pulseProgress.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                pulseProgress.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
            }
        } else if (!allDone) {
            // Re-opening or unticking — reset without animating.
            checkScale.snapTo(0f)
            pulseProgress.snapTo(0f)
        }
        hasInitialized = true
    }

    // Pulse the container from primaryContainer toward primary and back on
    // completion, then settle. Steady-state color is unchanged.
    val baseContainer = if (allDone)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerLow
    val pulseTarget = MaterialTheme.colorScheme.primary
    val containerColor = lerp(baseContainer, pulseTarget, pulseProgress.value)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale.value),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = if (!allDone) BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // AnimatedVisibility collapses the icon + spacer slot when
                    // not complete so the title row isn't padded with empty
                    // space. The bouncy graphicsLayer scale plays on top of
                    // the slot reveal for the "pop" effect.
                    AnimatedVisibility(
                        visible = allDone,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer {
                                        scaleX = checkScale.value
                                        scaleY = checkScale.value
                                    },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                    Text(
                        text = if (allDone) "All done!"
                               else "$completedCount of $totalItems picked up",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (allDone)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Percentage badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (allDone)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (allDone)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Progress bar — animates smoothly as items get checked off.
            // Uses the primary color so it ties into the overall theme.
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}
