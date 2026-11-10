package com.radley.homefit.ui.theme

import android.app.Activity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * HomeFit is dark-only on purpose: the Deep Teal palette is built around a near-black ground with
 * one bright accent, and a synthesised light scheme would lose exactly that contrast. So the
 * system dark-mode setting is deliberately not consulted.
 */
private val DeepTeal = darkColorScheme(
    primary = Accent,
    onPrimary = Bg,
    primaryContainer = AccentContainer,
    onPrimaryContainer = TextColor,
    inversePrimary = Accent,
    secondary = AccentSoft,
    onSecondary = Bg,
    secondaryContainer = AccentContainer,
    onSecondaryContainer = TextColor,
    tertiary = AccentSoft,
    onTertiary = Bg,
    tertiaryContainer = SurfaceHigh,
    onTertiaryContainer = TextColor,
    background = Bg,
    onBackground = TextColor,
    surface = Bg,
    onSurface = TextColor,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Muted,
    surfaceTint = Accent,
    inverseSurface = TextColor,
    inverseOnSurface = Bg,
    surfaceBright = SurfaceHigh,
    surfaceDim = Bg,
    surfaceContainerLowest = Bg,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigh,
    outline = Outline,
    outlineVariant = OutlineFaint,
    error = Danger,
    onError = Bg,
    scrim = Color.Black,
)

@Composable
fun HomeFitTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(colorScheme = DeepTeal, typography = HomeFitTypography) {
        // Material's default content colour is black, which only Surface and Scaffold replace.
        // Screens drawn straight on the window (onboarding, the player, the finish screen) would
        // otherwise render unstyled text black on the near-black ground.
        CompositionLocalProvider(LocalContentColor provides DeepTeal.onBackground, content = content)
    }
}
