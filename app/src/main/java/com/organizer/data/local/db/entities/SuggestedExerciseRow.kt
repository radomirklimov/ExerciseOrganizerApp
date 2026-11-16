package com.organizer.data.local.db.entities

import androidx.room.Embedded

/**
 * One exercise row together with the workout it was used in. Used to build the
 * Create Workout suggestions from several workouts instead of a single one.
 */
data class SuggestedExerciseRow(
    @Embedded val exercise: ExerciseEntity,
    val sourceWorkoutId: Long,
)
