package com.babegetthis.android.core.voice.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Decorative equaliser waveform shown while audio is being transcribed.
//
// It is NOT a live mic visualization — recording has already stopped and the
// backend streams no progress, so there's nothing real to plot. It's a looping
// "I'm working on it" animation, in the universal voice-app idiom (bars), which
// reads better than a bare spinner. Each bar's height pulses on a reversing
// loop, with a per-bar start offset so they ripple instead of pulsing in unison.
@Composable
fun TranscribingWaveform(
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    Row(
        modifier = modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(barCount) { index ->
            val heightFraction by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    // FastForward keeps the bars permanently staggered (a plain
                    // delay only offsets the first cycle, then they'd sync up).
                    initialStartOffset = StartOffset(index * 120, StartOffsetType.FastForward),
                ),
                label = "bar-$index",
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}
