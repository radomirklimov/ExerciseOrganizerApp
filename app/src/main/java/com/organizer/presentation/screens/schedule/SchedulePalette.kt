package com.organizer.presentation.screens.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.organizer.ui.theme.themedColor

// Palette taken from the Schedule design: a slightly warmer purple than the
// workout builder's, on the same near-white surface. Every value has a dark
// counterpart so the screen follows the app theme.

internal val ScheduleBrand: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF6B4FBB), Color(0xFFC3B2FF))
internal val ScheduleBrandDark: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF5A3DA8), Color(0xFFDACFFF))
internal val ScheduleBrand50: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF5F3FF), Color(0xFF2A2340))
internal val ScheduleBrand100: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFEDE9FE), Color(0xFF352A52))
internal val ScheduleInk: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF141B2B), Color(0xFFF2EEF9))
internal val ScheduleInkSecondary: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF64748B), Color(0xFFC2BBCE))
internal val ScheduleInkMuted: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF94A3B8), Color(0xFF948CA3))
internal val ScheduleBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFFEFBFE), Color(0xFF121016))
internal val ScheduleSurface: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color.White, Color(0xFF1C1921))
internal val ScheduleCardBorder: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF0EAFB), Color(0xFF2F2839))
internal val ScheduleFieldBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF8FAFC), Color(0xFF221E29))
internal val ScheduleFieldBorder: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFE2E8F0), Color(0xFF3A3344))
internal val ScheduleDivider: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF1F5F9), Color(0xFF2A2532))
internal val ScheduleDayBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFF8FAFC), Color(0xFF221E29))
internal val ScheduleRestDayBackground: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFFBFAFF), Color(0xFF1C1921))
internal val ScheduleRestDayBorder: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFE2E8F0), Color(0xFF3A3344))
internal val ScheduleTodayGreen: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF047857), Color(0xFF4ADE80))
internal val ScheduleTodayGreenBg: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFECFDF5), Color(0xFF14301F))
internal val ScheduleStartWorkoutGreen: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFF16A34A), Color(0xFF22C55E))
internal val ScheduleDanger: Color
    @Composable @ReadOnlyComposable get() = themedColor(Color(0xFFB3261E), Color(0xFFFFB4AB))
