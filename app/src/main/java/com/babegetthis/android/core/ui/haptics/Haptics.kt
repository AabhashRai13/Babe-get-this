package com.babegetthis.android.core.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// Named haptic intents — call sites read like "what does this mean?"
// not "which physical buzz did I pick?". When Compose 1.9 lands with
// HapticFeedbackType.Confirm / Reject, we change one when-branch and
// every call site upgrades for free.
enum class Haptic { Light, Medium, Heavy, Success }

// Compose-friendly helper. Returns a (Haptic) -> Unit you call inline:
//   val haptic = rememberHaptic()
//   onClick = { haptic(Haptic.Medium); doThing() }
//
// Must be called inside a @Composable because LocalHapticFeedback is
// only valid in composition — a top-level singleton wouldn't see the
// right haptic service for the current window.
@Composable
fun rememberHaptic(): (Haptic) -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { type ->
            when (type) {
                Haptic.Light -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                Haptic.Medium,
                Haptic.Heavy,
                Haptic.Success -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
}
