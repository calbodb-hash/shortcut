package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ShortCutDarkColorScheme = darkColorScheme(
    primary = ShortCutAccent,
    onPrimary = TextHighContrast,
    primaryContainer = ShortCutAccentVariant,
    onPrimaryContainer = TextHighContrast,
    secondary = ShortCutCyan,
    onSecondary = DarkCanvas,
    secondaryContainer = DarkSurfaceActive,
    onSecondaryContainer = ShortCutCyan,
    tertiary = ShortCutPurple,
    onTertiary = TextHighContrast,
    background = DarkCanvas,
    onBackground = TextHighContrast,
    surface = DarkSurface,
    onSurface = TextHighContrast,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = TextMediumContrast,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    outline = DarkSurfaceBorder,
    outlineVariant = DarkSurfaceActive,
    error = ShortCutAccent,
    onError = TextHighContrast
)

@Composable
fun ShortCutTheme(
    darkTheme: Boolean = true, // Video editor is strictly dark-themed for professional grade eye comfort
    content: @Composable () -> Unit
) {
    val colorScheme = ShortCutDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DarkCanvas.toArgb()
                window.navigationBarColor = DarkCanvas.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShortCutTypography,
        content = content
    )
}
