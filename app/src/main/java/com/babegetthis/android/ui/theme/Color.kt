package com.babegetthis.android.ui.theme

import androidx.compose.ui.graphics.Color

// Light theme colors
val Primary = Color(0xFF607D8B)
val DarkPrimary = Color(0xFF455A64)
val LightPrimary = Color(0xFFCFD8DC)
val Accent = Color(0xFF795548)
val Background = Color(0xFFFBFCFD)
val Surface = Color(0xFFFBFCFD)
val PrimaryText = Color(0xFF212121)
val SecondaryText = Color(0xFF757575)
val Divider = Color(0xFFBDBDBD)

// MD3 surface container levels — these create subtle visual layers.
// Think of them as different "elevations" of white, each slightly tinted
// with the primary color. This is how MD3 creates depth without shadows.
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF5F7F8)
val SurfaceContainer = Color(0xFFEFF2F3)
val SurfaceContainerHigh = Color(0xFFE9ECEE)
val SurfaceContainerHighest = Color(0xFFE3E7E9)

// Dark theme colors — lighter versions so they're readable on dark backgrounds
val DarkThemePrimary = Color(0xFF90A4AE)
val DarkThemeAccent = Color(0xFFA1887F)
val DarkThemeBackground = Color(0xFF121212)
val DarkThemeSurface = Color(0xFF141718)
val DarkThemePrimaryText = Color(0xFFFFFFFF)
val DarkThemeSecondaryText = Color(0xFFB0B0B0)
val DarkThemeDivider = Color(0xFF424242)

// Dark theme surface containers
val DarkSurfaceContainerLowest = Color(0xFF0E1112)
val DarkSurfaceContainerLow = Color(0xFF1A1D1F)
val DarkSurfaceContainer = Color(0xFF1E2224)
val DarkSurfaceContainerHigh = Color(0xFF282C2E)
val DarkSurfaceContainerHighest = Color(0xFF333739)

// List accent palette — dusty, muted tones that complement the blue-grey/brown theme.
// Not neon or childish — these feel warm and premium.
// Each pair is (background, onBackground) so text/icons are always readable.
data class ListAccentColor(
    val container: Color,
    val onContainer: Color,
)

val ListAccentPalette = listOf(
    ListAccentColor(Color(0xFFE8D5D1), Color(0xFF5D3A32)),  // Dusty rose
    ListAccentColor(Color(0xFFD5DED6), Color(0xFF2E4433)),  // Sage green
    ListAccentColor(Color(0xFFD6DDE8), Color(0xFF2E3D52)),  // Soft slate blue
    ListAccentColor(Color(0xFFE6D9C3), Color(0xFF4D3E28)),  // Warm sand
    ListAccentColor(Color(0xFFDAD3E6), Color(0xFF3D3452)),  // Dusty lavender
    ListAccentColor(Color(0xFFCFDDDB), Color(0xFF2A4240)),  // Muted teal
    ListAccentColor(Color(0xFFE8D2C0), Color(0xFF523A24)),  // Terracotta
    ListAccentColor(Color(0xFFD4DAE0), Color(0xFF333D47)),  // Cool grey-blue
)

// Dark theme versions — same hues but tuned for dark backgrounds.
// Containers are dark and muted, text/icons are lighter pastels.
val DarkListAccentPalette = listOf(
    ListAccentColor(Color(0xFF3D2C28), Color(0xFFD4ACA3)),  // Dusty rose
    ListAccentColor(Color(0xFF263330), Color(0xFFA3C4A8)),  // Sage green
    ListAccentColor(Color(0xFF252D38), Color(0xFFA3B5CC)),  // Soft slate blue
    ListAccentColor(Color(0xFF352E1F), Color(0xFFCCBA97)),  // Warm sand
    ListAccentColor(Color(0xFF2D2838), Color(0xFFB3A5CC)),  // Dusty lavender
    ListAccentColor(Color(0xFF223230), Color(0xFF97BAB6)),  // Muted teal
    ListAccentColor(Color(0xFF382A1E), Color(0xFFCCAA8D)),  // Terracotta
    ListAccentColor(Color(0xFF282D33), Color(0xFFA3B0BD)),  // Cool grey-blue
)
