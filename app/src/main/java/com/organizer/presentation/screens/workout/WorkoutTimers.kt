package com.organizer.presentation.screens.workout

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Sentinel stored instead of a real clock reading to mean "no anchor set",
 * e.g. an elapsed timer that has not stopped yet or a stopwatch that is
 * currently paused.
 */
internal const val NO_TIMER_ANCHOR: Long = Long.MIN_VALUE

/**
 * Seconds elapsed between [startRealtime] and [endRealtime].
 *
 * [nowRealtime] is only used while the timer is running ([endRealtime] is
 * [NO_TIMER_ANCHOR]). Both readings must come from the same monotonic clock
 * (`SystemClock.elapsedRealtime()` in production), which keeps advancing while
 * the app is in the background, the screen is off or the device is dozing.
 * Because the value is derived from clock readings instead of counting ticks,
 * a ticker that stops firing for a while cannot lose time.
 */
internal fun elapsedSecondsSince(
    startRealtime: Long,
    endRealtime: Long,
    nowRealtime: Long,
): Int {
    val until = if (endRealtime == NO_TIMER_ANCHOR) nowRealtime else endRealtime
    return ((until - startRealtime).coerceAtLeast(0L) / 1000L).toInt()
}

/**
 * Stopwatch bookkeeping for duration exercises.
 *
 * Only clock readings are stored: [accumulatedMs] covers the run segments that
 * already ended and [runningSinceRealtime] is the start of the current one.
 * The elapsed value is always derived from them, so background time keeps
 * counting and a paused stopwatch stays frozen without drifting.
 */
internal data class StopwatchState(
    val accumulatedMs: Long = 0L,
    val runningSinceRealtime: Long = NO_TIMER_ANCHOR,
) {
    val running: Boolean
        get() = runningSinceRealtime != NO_TIMER_ANCHOR

    /** Measured time so far. [nowRealtime] is ignored while paused. */
    fun elapsedMs(nowRealtime: Long): Long {
        val currentSegment = if (running) {
            (nowRealtime - runningSinceRealtime).coerceAtLeast(0L)
        } else {
            0L
        }
        return accumulatedMs + currentSegment
    }

    fun elapsedSeconds(nowRealtime: Long): Int = (elapsedMs(nowRealtime) / 1000L).toInt()

    /** Begins a fresh measurement from zero. */
    fun start(nowRealtime: Long): StopwatchState =
        StopwatchState(accumulatedMs = 0L, runningSinceRealtime = nowRealtime)

    /** Freezes the measurement, keeping everything measured so far. */
    fun pause(nowRealtime: Long): StopwatchState =
        if (running) {
            StopwatchState(
                accumulatedMs = elapsedMs(nowRealtime),
                runningSinceRealtime = NO_TIMER_ANCHOR,
            )
        } else {
            this
        }

    /** Continues a paused measurement from where it stopped. */
    fun resume(nowRealtime: Long): StopwatchState =
        if (running) this else copy(runningSinceRealtime = nowRealtime)

    /** Ends the measurement; a later [start] begins a new one from zero. */
    fun stop(nowRealtime: Long): StopwatchState = pause(nowRealtime)
}

/** Flattens the stopwatch into primitives a Bundle can hold. */
internal fun StopwatchState.toSaveable(): List<Long> =
    listOf(accumulatedMs, runningSinceRealtime)

/** Rebuilds a stopwatch saved with [toSaveable]. */
internal fun stopwatchStateFromSaveable(saved: List<Long>): StopwatchState =
    StopwatchState(
        accumulatedMs = saved[0],
        runningSinceRealtime = saved[1],
    )

/** Saves the stopwatch across configuration changes and process death. */
internal val StopwatchStateSaver: Saver<StopwatchState, Any> = listSaver(
    save = { it.toSaveable() },
    restore = { stopwatchStateFromSaveable(it) },
)
