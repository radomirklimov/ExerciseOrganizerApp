package com.organizer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors used by the profile feature. This is intentionally separate from the
 * app theme: the profile design is still being reviewed, so its settings do
 * not repaint the rest of the app yet.
 */
@Immutable
data class AppPalette(
    val background: Color,
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
)

val LightAppPalette = AppPalette(
    background = Color(0xFFFEFBFE),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF6F2F7),
    surfaceStrong = Color(0xFFF0EDF1),
    border = Color(0xFFE7E3EC),
    borderStrong = Color(0xFFCBC4D2),
    divider = Color(0xFFF0EEF3),
    textPrimary = Color(0xFF1B1B1E),
    textSecondary = Color(0xFF484552),
    textMuted = Color(0xFF7A7582),
    primary = Color(0xFF5C4BB7),
    primaryDark = Color(0xFF44319E),
    primarySoft = Color(0xFFEDE8F8),
    onPrimary = Color(0xFFFFFFFF),
    success = Color(0xFF16A34A),
    successSoft = Color(0xFFE8F8EE),
    successBorder = Color(0xFFBBF7D0),
    danger = Color(0xFFBA1A1A),
    dangerSoft = Color(0xFFFFDAD6),
    dangerBorder = Color(0xFFF2B8B5),
)

val LocalAppPalette = staticCompositionLocalOf { LightAppPalette }

/** Convenience access for the profile feature. */
object AppTheme {
    val colors: AppPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppPalette.current
}
