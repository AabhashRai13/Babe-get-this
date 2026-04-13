package com.babegetthis.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Background,
    primaryContainer = LightPrimary,
    onPrimaryContainer = DarkPrimary,

    secondary = Accent,
    onSecondary = Background,

    background = Background,
    onBackground = PrimaryText,

    surface = Surface,
    onSurface = PrimaryText,
    onSurfaceVariant = SecondaryText,

    outline = Divider
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkThemePrimary,
    onPrimary = DarkThemeBackground,
    primaryContainer = DarkPrimary,
    onPrimaryContainer = LightPrimary,

    secondary = DarkThemeAccent,
    onSecondary = DarkThemeBackground,

    background = DarkThemeBackground,
    onBackground = DarkThemePrimaryText,

    surface = DarkThemeSurface,
    onSurface = DarkThemePrimaryText,
    onSurfaceVariant = DarkThemeSecondaryText,

    outline = DarkThemeDivider
)

@Composable
fun BabeGetThisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color overrides your palette with the user's wallpaper colors.
    // Set to false so your custom palette is always used.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
