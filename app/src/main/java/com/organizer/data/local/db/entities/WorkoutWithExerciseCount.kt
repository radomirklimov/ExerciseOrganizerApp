package com.organizer.data.local.db.entities

/** Workout row plus the number of exercises it contains. */
data class WorkoutWithExerciseCount(
    val workoutId: Long,
    val name: String,
    val exerciseCount: Int,
)
