package com.babegetthis.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// Roboto is Compose's default font, so no need to load it manually.
// This is like Flutter where you'd set fontFamily: 'Roboto' in your TextTheme,
// except Compose already does it for you.
val Typography = Typography(
    // All styles inherit Roboto by default.
    // Customize individual styles here as needed, e.g.:
    // headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)
)
