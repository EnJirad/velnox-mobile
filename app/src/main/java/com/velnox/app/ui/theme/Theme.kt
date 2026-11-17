package com.velnox.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color

// Velnox light scheme — white surfaces on a slate-50 page, dark primary,
// emerald brand accent (§2, §3).
private val VelnoxLightColors = lightColorScheme(
    primary = VelnoxPrimary,
    onPrimary = VelnoxPrimaryForeground,
    primaryContainer = VelnoxSecondary,
    onPrimaryContainer = VelnoxPrimary,
    secondary = VelnoxBrand,
    onSecondary = VelnoxBrandForeground,
    secondaryContainer = VelnoxBrandSoft,
    onSecondaryContainer = VelnoxBrandSoftForeground,
    tertiary = VelnoxSlate600,
    onTertiary = VelnoxPrimaryForeground,
    background = VelnoxBackground,
    onBackground = VelnoxForeground,
    surface = VelnoxCard,
    onSurface = VelnoxForeground,
    surfaceVariant = VelnoxSecondary,
    onSurfaceVariant = VelnoxMutedForeground,
    surfaceContainer = VelnoxCard,
    surfaceContainerHigh = VelnoxCard,
    outline = VelnoxBorder,
    outlineVariant = VelnoxSlate100,
    error = VelnoxDestructive,
    onError = VelnoxDestructiveForeground,
    errorContainer = VelnoxDestructiveSoft,
    onErrorContainer = VelnoxDestructiveSoftForeground,
    scrim = Color(0x66000000),
    inverseSurface = VelnoxPrimary,
    inverseOnSurface = VelnoxPrimaryForeground,
)

// Slate is the dark neutral of the system, white is the primary action there.
private val VelnoxDarkColors = darkColorScheme(
    primary = Color(0xFFF8FAFC),
    onPrimary = VelnoxPrimary,
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = VelnoxSlate100,
    secondary = VelnoxBrand,
    onSecondary = VelnoxPrimary,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = VelnoxSlate300,
    onTertiary = VelnoxPrimary,
    background = Color(0xFF020617),
    onBackground = VelnoxSlate50,
    surface = Color(0xFF0B1220),
    onSurface = VelnoxSlate50,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = VelnoxSlate400,
    surfaceContainer = Color(0xFF0B1220),
    surfaceContainerHigh = Color(0xFF111C31),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onError = VelnoxPrimary,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFEE2E2),
    scrim = Color(0x99000000),
    inverseSurface = VelnoxSlate50,
    inverseOnSurface = VelnoxPrimary,
)

@Composable
fun VelnoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) VelnoxDarkColors else VelnoxLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VelnoxTypography,
        shapes = VelnoxShapes,
        content = content,
    )
}
