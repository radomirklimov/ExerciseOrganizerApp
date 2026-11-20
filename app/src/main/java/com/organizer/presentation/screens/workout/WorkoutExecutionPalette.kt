package com.organizer.presentation.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.organizer.ui.theme.themedColor

// Palette taken from the Workout Execution design: a slightly cooler purple
// than the schedule page, on a slate-tinted background. Each value has a dark
// counterpart so the player follows the app theme.

internal val ExecutionBrand: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF6347EB), Color(0xFFB9A6FF))
internal val ExecutionBrandDark: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF4A26D3), Color(0xFFD4C6FF))
internal val ExecutionBrand50: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF5F3FF), Color(0xFF272040))
internal val ExecutionBrand100: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFEDE9FE), Color(0xFF332A55))
internal val ExecutionBrand200: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFDDD6FE), Color(0xFF4A3D78))
internal val ExecutionBrand900: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF301880), Color(0xFFE5DCFF))

internal val ExecutionGreen: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF16A34A), Color(0xFF4ADE80))
internal val ExecutionGreenDark: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF15803D), Color(0xFF22C55E))
internal val ExecutionGreenLight: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFE8F8EE), Color(0xFF16301F))
internal val ExecutionGreenBorder: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFBBF7D0), Color(0xFF245C38))

internal val ExecutionRed: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFDC2626), Color(0xFFFF6B6B))

internal val ExecutionBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF8FAFC), Color(0xFF121016))
internal val ExecutionSurface: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color.White, Color(0xFF1C1921))
internal val ExecutionSurfaceMuted: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF8FAFC), Color(0xFF221E29))
internal val ExecutionSurfaceStrong: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF1F5F9), Color(0xFF2A2532))
internal val ExecutionCardBorder: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF1F5F9), Color(0xFF2A2532))
internal val ExecutionFieldBorder: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFE2E8F0), Color(0xFF3A3344))

internal val ExecutionInk: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF0F172A), Color(0xFFF2EEF9))
internal val ExecutionInkSecondary: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF64748B), Color(0xFFC2BBCE))
internal val ExecutionInkMuted: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF94A3B8), Color(0xFF948CA3))
