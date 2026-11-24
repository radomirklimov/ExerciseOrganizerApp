package com.organizer.presentation.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutCompletionTest {

    @Test
    fun `empty workout has no success percentage`() {
        assertEquals(0, completionPercent(completed = 0, total = 0))
        assertEquals(0, completionPercent(completed = 3, total = 0))
    }

    @Test
    fun `all exercises completed is one hundred percent`() {
        assertEquals(100, completionPercent(completed = 5, total = 5))
    }

    @Test
    fun `skipped exercises reduce the success percentage`() {
        assertEquals(0, completionPercent(completed = 0, total = 5))
        assertEquals(20, completionPercent(completed = 1, total = 5))
        assertEquals(60, completionPercent(completed = 3, total = 5))
        assertEquals(80, completionPercent(completed = 4, total = 5))
    }

    @Test
    fun `percentage is rounded to the nearest whole number`() {
        assertEquals(33, completionPercent(completed = 1, total = 3))
        assertEquals(67, completionPercent(completed = 2, total = 3))
        assertEquals(13, completionPercent(completed = 1, total = 8))
    }

    @Test
    fun `percentage stays within zero and one hundred`() {
        assertEquals(0, completionPercent(completed = -1, total = 5))
        assertEquals(100, completionPercent(completed = 10, total = 5))
    }
}
