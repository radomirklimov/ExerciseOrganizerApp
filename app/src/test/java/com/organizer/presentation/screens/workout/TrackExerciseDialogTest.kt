package com.organizer.presentation.screens.workout

import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackExerciseDialogTest {
    @Test
    fun `set type is inferred from recorded metrics`() {
        val durationSet = ExerciseProgressEntity(
            workoutId = 1,
            exerciseId = 1,
            setNumber = 1,
            durationSeconds = 45,
        )
        val repsSet = ExerciseProgressEntity(
            workoutId = 1,
            exerciseId = 1,
            setNumber = 2,
            reps = 12,
        )
        val weightedSet = ExerciseProgressEntity(
            workoutId = 1,
            exerciseId = 1,
            setNumber = 3,
            reps = 10,
            weight = 15.0,
        )

        assertEquals("DURATION", durationSet.inferredExerciseType())
        assertEquals("REPS", repsSet.inferredExerciseType())
        assertEquals("REPS_WEIGHT", weightedSet.inferredExerciseType())
    }

    @Test
    fun `weight formatting drops unnecessary decimals`() {
        assertEquals("0 kg", formatWeight(0.0))
        assertEquals("15 kg", formatWeight(15.0))
        assertEquals("15.5 kg", formatWeight(15.5))
    }

    @Test
    fun `workout type follows the most common set type`() {
        val exercise = exercise(type = "REPS_WEIGHT")
        val sets = listOf(
            set(setNumber = 1, reps = 10),
            set(setNumber = 2, reps = 12),
            set(setNumber = 3, durationSeconds = 30),
        )

        assertEquals("REPS", effectiveExerciseType(exercise, sets))
    }

    @Test
    fun `workout type uses the first set when all types are tied`() {
        val exercise = exercise(type = "REPS_WEIGHT")
        val sets = listOf(
            set(setNumber = 1, durationSeconds = 30),
            set(setNumber = 2, reps = 10),
            set(setNumber = 3, reps = 12, weight = 20.0),
        )

        assertEquals("DURATION", effectiveExerciseType(exercise, sets))
    }

    @Test
    fun `workout type uses the first tied type when another type has fewer sets`() {
        val exercise = exercise(type = "REPS_WEIGHT")
        val sets = listOf(
            set(setNumber = 1, reps = 10),
            set(setNumber = 2, durationSeconds = 30),
            set(setNumber = 3, reps = 12, weight = 20.0),
            set(setNumber = 4, durationSeconds = 45),
            set(setNumber = 5, reps = 15, weight = 25.0),
        )

        assertEquals("DURATION", effectiveExerciseType(exercise, sets))
    }

    @Test
    fun `workout type uses the stored type when there are no sets`() {
        assertEquals("DURATION", effectiveExerciseType(exercise(type = "DURATION"), emptyList()))
    }

    private fun exercise(type: String) = ExerciseEntity(
        exerciseId = 1,
        name = "Bench Press",
        instructions = emptyList(),
        imageUrls = emptyList(),
        isCustom = false,
        exerciseType = type,
    )

    private fun set(
        setNumber: Int,
        reps: Int? = null,
        weight: Double? = null,
        durationSeconds: Int? = null,
    ) = ExerciseProgressEntity(
        workoutId = 1,
        exerciseId = 1,
        setNumber = setNumber,
        reps = reps,
        weight = weight,
        durationSeconds = durationSeconds,
    )
}
