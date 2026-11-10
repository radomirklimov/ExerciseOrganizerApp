package com.organizer.presentation.screens.workout

import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `duration formatting uses hours minutes and seconds`() {
        assertEquals("0s", formatDuration(0))
        assertEquals("30s", formatDuration(30))
        assertEquals("5m 30s", formatDuration(330))
        assertEquals("1h 5m 30s", formatDuration(3930))
        assertEquals("1h 0m 5s", formatDuration(3605))
        assertEquals("1h 5m 0s", formatDuration(3900))
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

    @Test
    fun `drag steps accelerate with distance and follow direction`() {
        assertEquals(0, dragStepsForDistance(0f, 1f))
        assertEquals(1, dragStepsForDistance(8f, 1f))
        assertEquals(-1, dragStepsForDistance(-8f, 1f))
        assertEquals(1, dragStepsForDistance(16f, 2f))

        val shortDrag = dragStepsForDistance(16f, 1f)
        val longDrag = dragStepsForDistance(64f, 1f)
        assertTrue(longDrag > shortDrag * 4)
    }

    @Test
    fun `drag values are not capped at the old limits`() {
        assertEquals(150, adjustedDragValue(100, 50, 1, 0..Int.MAX_VALUE))
        assertEquals(6000, adjustedDragValue(5000, 200, 5, 0..Int.MAX_VALUE))
    }

    @Test
    fun `drag value arithmetic saturates instead of overflowing`() {
        assertEquals(
            Int.MAX_VALUE,
            adjustedDragValue(Int.MAX_VALUE - 1, Int.MAX_VALUE, 5, 0..Int.MAX_VALUE),
        )
        assertEquals(0, adjustedDragValue(1, -Int.MAX_VALUE, 5, 0..Int.MAX_VALUE))
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
