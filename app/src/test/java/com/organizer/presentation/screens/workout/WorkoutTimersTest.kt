package com.organizer.presentation.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTimersTest {

    @Test
    fun `elapsed timer counts time that passed while the ticker was frozen`() {
        // Started one second after boot, checked 300 seconds later: the value
        // comes from the clock readings, so a stalled ticker loses nothing.
        assertEquals(
            300,
            elapsedSecondsSince(
                startRealtime = 1_000L,
                endRealtime = NO_TIMER_ANCHOR,
                nowRealtime = 301_000L,
            ),
        )
    }

    @Test
    fun `elapsed timer freezes at the finish reading`() {
        assertEquals(
            60,
            elapsedSecondsSince(
                startRealtime = 1_000L,
                endRealtime = 61_000L,
                nowRealtime = 10_000_000L,
            ),
        )
    }

    @Test
    fun `elapsed timer never reports negative time`() {
        // A reboot resets elapsedRealtime; the anchor from before is larger.
        assertEquals(
            0,
            elapsedSecondsSince(
                startRealtime = 500_000L,
                endRealtime = NO_TIMER_ANCHOR,
                nowRealtime = 1_000L,
            ),
        )
    }

    @Test
    fun `elapsed timer reports zero before its start anchor is set`() {
        assertEquals(
            0,
            elapsedSecondsSince(
                startRealtime = NO_TIMER_ANCHOR,
                endRealtime = NO_TIMER_ANCHOR,
                nowRealtime = 10_000L,
            ),
        )
    }

    @Test
    fun `start countdown shows three then two then one then zero`() {
        val start = 5_000L
        assertEquals(3, countdownRemaining(start, start))
        assertEquals(3, countdownRemaining(start, start + 999L))
        assertEquals(2, countdownRemaining(start, start + 1_000L))
        assertEquals(1, countdownRemaining(start, start + 2_500L))
        assertEquals(0, countdownRemaining(start, start + 3_000L))
    }

    @Test
    fun `start countdown cannot be extended by a frozen ticker`() {
        // The ticker did not run for a minute; the monotonic anchor still says
        // the countdown is over, so the workout must start immediately.
        assertEquals(0, countdownRemaining(1_000L, 61_000L))
    }

    @Test
    fun `start countdown survives a clock reset and a missing anchor`() {
        assertEquals(3, countdownRemaining(500_000L, 1_000L))
        assertEquals(0, countdownRemaining(NO_TIMER_ANCHOR, 1_000L))
    }

    @Test
    fun `stopwatch measures only the running segments`() {
        var timer = StopwatchState()

        timer = timer.start(nowRealtime = 1_000L)
        assertTrue(timer.running)
        assertEquals(0, timer.elapsedSeconds(1_000L))

        // Ran for five seconds, then paused.
        timer = timer.pause(nowRealtime = 6_000L)
        assertFalse(timer.running)
        assertEquals(5, timer.elapsedSeconds(6_000L))

        // Paused time is not measured, even much later.
        assertEquals(5, timer.elapsedSeconds(100_000L))

        // Resumed ten seconds later and ran two more seconds before stopping.
        timer = timer.resume(nowRealtime = 110_000L)
        timer = timer.stop(nowRealtime = 112_000L)
        assertFalse(timer.running)
        assertEquals(7, timer.elapsedSeconds(500_000L))
    }

    @Test
    fun `stopwatch keeps counting while the app is in the background`() {
        val running = StopwatchState().start(nowRealtime = 2_000L)

        // The ticker did not fire at all: the value still follows the clock.
        assertEquals(600, running.elapsedSeconds(2_000L + 600_000L))
    }

    @Test
    fun `stopwatch start begins a fresh measurement after stopping`() {
        val stopped = StopwatchState()
            .start(nowRealtime = 1_000L)
            .stop(nowRealtime = 9_000L)
        assertEquals(8, stopped.elapsedSeconds(9_000L))

        val restarted = stopped.start(nowRealtime = 20_000L)
        assertEquals(0, restarted.elapsedSeconds(20_000L))
        assertEquals(3, restarted.elapsedSeconds(23_000L))
    }

    @Test
    fun `stopwatch pause and resume are idempotent`() {
        val paused = StopwatchState()
            .start(nowRealtime = 1_000L)
            .pause(nowRealtime = 3_000L)
            .pause(nowRealtime = 3_000L)
        assertEquals(2, paused.elapsedSeconds(3_000L))

        val running = StopwatchState().start(nowRealtime = 1_000L)
        val resumedTwice = running
            .resume(nowRealtime = 2_000L)
            .resume(nowRealtime = 5_000L)
        // The second resume must not move the segment start.
        assertEquals(4, resumedTwice.elapsedSeconds(5_000L))
    }

    @Test
    fun `stopwatch survives save and restore`() {
        val saved = StopwatchState()
            .start(nowRealtime = 1_000L)
            .pause(nowRealtime = 4_500L)

        val restored = roundTrip(saved)

        assertEquals(saved, restored)
        assertEquals(saved.elapsedSeconds(4_500L), restored.elapsedSeconds(4_500L))
    }

    /**
     * Rounds the stopwatch through the primitives used by rememberSaveable.
     */
    private fun roundTrip(state: StopwatchState): StopwatchState =
        stopwatchStateFromSaveable(state.toSaveable())
}
