package com.organizer.presentation.screens.workout

/**
 * Rounded percentage of exercises the user actually finished.
 *
 * Skipped exercises are excluded from [completed], so this is the success rate
 * of the session rather than merely its progress. Uses the same rounding as
 * the completion stored for the schedule calendar.
 */
internal fun completionPercent(completed: Int, total: Int): Int {
    if (total <= 0 || completed <= 0) return 0
    return ((completed * 100 + total / 2) / total).coerceIn(0, 100)
}
