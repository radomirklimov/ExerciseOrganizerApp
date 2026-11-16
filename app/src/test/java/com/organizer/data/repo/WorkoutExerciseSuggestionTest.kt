package com.organizer.data.repo

import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SuggestedExerciseRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WorkoutExerciseSuggestionTest {

    @Test
    fun `suggestions mix all workouts with the newest one first`() {
        val rows = listOf(
            row(workoutId = 1, exerciseId = 1),
            row(workoutId = 1, exerciseId = 2),
            row(workoutId = 2, exerciseId = 3),
            row(workoutId = 2, exerciseId = 4),
            row(workoutId = 3, exerciseId = 5),
            row(workoutId = 3, exerciseId = 6),
        )

        val result = mixSuggestedExercises(rows, limit = 6, random = Random(7))

        assertEquals(6, result.size)
        // Round-robin: newest, middle, oldest, then newest again, ...
        assertEquals(listOf(3, 2, 1, 3, 2, 1), result.map { workoutOf(it.exerciseId) })
    }

    @Test
    fun `recent workouts fill the list before older ones`() {
        val rows = listOf(
            row(workoutId = 1, exerciseId = 1),
            row(workoutId = 2, exerciseId = 2),
            row(workoutId = 3, exerciseId = 3),
        )

        val result = mixSuggestedExercises(rows, limit = 2, random = Random(1))

        assertEquals(setOf(2L, 3L), result.map { it.exerciseId }.toSet())
    }

    @Test
    fun `an exercise used in several workouts is suggested once`() {
        val rows = listOf(
            row(workoutId = 1, exerciseId = 1),
            row(workoutId = 1, exerciseId = 2),
            row(workoutId = 2, exerciseId = 1),
            row(workoutId = 2, exerciseId = 3),
        )

        val result = mixSuggestedExercises(rows, limit = 3, random = Random(3))

        assertEquals(3, result.size)
        assertEquals(3, result.map { it.exerciseId }.distinct().size)
        assertTrue(1L in result.map { it.exerciseId })
    }

    @Test
    fun `non-positive limit returns no suggestions`() {
        val rows = listOf(row(workoutId = 1, exerciseId = 1))

        assertEquals(emptyList<ExerciseEntity>(), mixSuggestedExercises(rows, limit = 0))
    }

    private fun workoutOf(exerciseId: Long): Int = when (exerciseId) {
        1L, 2L -> 1
        3L, 4L -> 2
        else -> 3
    }

    private fun row(workoutId: Long, exerciseId: Long) = SuggestedExerciseRow(
        exercise = ExerciseEntity(
            exerciseId = exerciseId,
            name = "Exercise $exerciseId",
            instructions = emptyList(),
            imageUrls = emptyList(),
            isCustom = exerciseId % 2L == 0L,
        ),
        sourceWorkoutId = workoutId,
    )
}
