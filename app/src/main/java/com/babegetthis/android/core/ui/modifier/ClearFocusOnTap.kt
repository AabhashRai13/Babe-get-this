package com.babegetthis.android.core.ui.modifier

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

// Tapping blank space clears focus, which also closes the keyboard (Compose
// hides the IME whenever the focused text field loses focus). Interactive
// children are unaffected — buttons and fields consume their own taps before
// this modifier ever sees them.
//
// Flutter equivalent: wrapping a form in
// GestureDetector(onTap: () => FocusScope.of(context).unfocus()).
//
// `composed {}` lets a plain Modifier extension read a composition local
// (LocalFocusManager) — like needing BuildContext inside a reusable widget.
fun Modifier.clearFocusOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}
