package com.organizer.presentation.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.organizer.ui.theme.themedColor

// Same palette as the Add to Workout sheet, so the workout builder and the
// exercise-to-workout flow look like one feature. Each value has a dark
// counterpart so the builder follows the app theme.

internal val Brand: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF6750A4), Color(0xFFC7B6FF))
internal val BrandDark: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF4F378A), Color(0xFFDCCFFF))
internal val Brand50: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF1ECF2), Color(0xFF2A2531))
internal val Brand200: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFCBC4D2), Color(0xFF4A4352))
internal val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF1C1B1F), Color(0xFFF4F0F7))
internal val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF7A7582), Color(0xFFB7AFBF))
internal val ScreenBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFFEFBFE), Color(0xFF121016))
internal val DividerColor: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF0EEF3), Color(0xFF2A2532))
internal val FieldBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF4F3F7), Color(0xFF221E29))
internal val RowBorder: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFE4E2EA), Color(0xFF3A3344))
internal val DisabledBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFE7E5EB), Color(0xFF2B2730))
internal val DisabledText: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFA9A4B2), Color(0xFF6E6777))
