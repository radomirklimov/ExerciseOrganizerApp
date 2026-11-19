package com.organizer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors shared by the whole app. The light values match the
 * existing design; the dark values are their counterparts on a near-black
 * background.
 */
@Immutable
data class AppPalette(
    val isDark: Boolean,
    val background: Color,
    val backgroundSubtle: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceStrong: Color,
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryDark: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val success: Color,
    val successSoft: Color,
    val successBorder: Color,
    val danger: Color,
    val dangerSoft: Color,
    val dangerBorder: Color,
    val favorite: Color,
    val favoriteSoft: Color,
)

val LightAppPalette = AppPalette(
    isDark = false,
    background = Color(0xFFFEFBFE),
    backgroundSubtle = Color(0xFFF8F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF6F2F7),
    surfaceStrong = Color(0xFFF0EDF1),
    border = Color(0xFFE7E3EC),
    borderStrong = Color(0xFFCBC4D2),
    divider = Color(0xFFF0EEF3),
    textPrimary = Color(0xFF1B1B1E),
    textSecondary = Color(0xFF484552),
    textMuted = Color(0xFF7A7582),
    primary = Color(0xFF6750A4),
    primaryDark = Color(0xFF44319E),
    primarySoft = Color(0xFFEDE8F8),
    onPrimary = Color(0xFFFFFFFF),
    success = Color(0xFF16A34A),
    successSoft = Color(0xFFE8F8EE),
    successBorder = Color(0xFFBBF7D0),
    danger = Color(0xFFBA1A1A),
    dangerSoft = Color(0xFFFFDAD6),
    dangerBorder = Color(0xFFF2B8B5),
    favorite = Color(0xFFE05275),
    favoriteSoft = Color(0xFFFBE4EC),
)

val DarkAppPalette = AppPalette(
    isDark = true,
    background = Color(0xFF121016),
    backgroundSubtle = Color(0xFF16141B),
    surface = Color(0xFF1C1921),
    surfaceMuted = Color(0xFF24202A),
    surfaceStrong = Color(0xFF2D2834),
    border = Color(0xFF37323F),
    borderStrong = Color(0xFF4B4456),
    divider = Color(0xFF2A2532),
    textPrimary = Color(0xFFF4F0F7),
    textSecondary = Color(0xFFC9C2D3),
    textMuted = Color(0xFF9A93A6),
    primary = Color(0xFFC9B8FF),
    primaryDark = Color(0xFF8A6BC8),
    primarySoft = Color(0xFF2E2646),
    onPrimary = Color(0xFF2A1758),
    success = Color(0xFF4ADE80),
    successSoft = Color(0xFF14301F),
    successBorder = Color(0xFF235C38),
    danger = Color(0xFFFFB4AB),
    dangerSoft = Color(0xFF4A1F1E),
    dangerBorder = Color(0xFF7A2E2B),
    favorite = Color(0xFFFF9FB6),
    favoriteSoft = Color(0xFF3A1D26),
)

val LocalAppPalette = staticCompositionLocalOf { LightAppPalette }

/** Convenience access for the app palette. */
object AppTheme {
    val colors: AppPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppPalette.current
}

/**
 * Picks [light] or [dark] depending on the current app palette. Handy for the
 * feature palettes that keep their own exact design values in light mode.
 */
@Composable
@ReadOnlyComposable
fun themedColor(light: Color, dark: Color): Color =
    if (LocalAppPalette.current.isDark) dark else light
