package com.organizer.presentation.screens.workout

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
}
