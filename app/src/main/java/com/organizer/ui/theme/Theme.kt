package com.organizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFEFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF1ECF2),
    onSurfaceVariant = Color(0xFF494551),
    outline = Color(0xFFCBC4D2),
    outlineVariant = Color(0xFFE7E3EC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC9B8FF),
    onPrimary = Color(0xFF2A1758),
    primaryContainer = Color(0xFF463278),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFC9C2D3),
    onSecondary = Color(0xFF322F38),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4A2530),
    background = Color(0xFF121016),
    onBackground = Color(0xFFF4F0F7),
    surface = Color(0xFF1C1921),
    onSurface = Color(0xFFF4F0F7),
    surfaceVariant = Color(0xFF24202A),
    onSurfaceVariant = Color(0xFFC9C2D3),
    outline = Color(0xFF4B4456),
    outlineVariant = Color(0xFF37323F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * Root theme for the Compose hierarchy. [darkTheme] follows the app's own
 * Light/Dark setting rather than the system setting.
 *
 * [fontScale] is applied on top of the system font scale through
 * [LocalDensity], so every `sp` text size in the app follows the profile
 * Font Size setting without touching individual screens.
 */
@Composable
fun AppThemeProvider(
    darkTheme: Boolean,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkAppPalette else LightAppPalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val density = LocalDensity.current

    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * fontScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
