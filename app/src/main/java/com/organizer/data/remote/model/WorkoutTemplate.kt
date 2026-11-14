package com.organizer.data.remote.model

import kotlinx.serialization.Serializable

/**
 * Starter workout template as returned by `/api/v1/starter-workouts`.
 * The backend reuses the workout model and flags templates with [starter].
 */
@Serializable
data class WorkoutTemplate(
    val workoutId: Long,
    val name: String,
    val notes: String = "",
    val starter: Boolean = false,
    val exercises: List<WorkoutTemplateExercise> = emptyList(),
)

@Serializable
data class WorkoutTemplateExercise(
    val exerciseId: Long = 0,
    val orderIndex: Int = 0,
    val exercise: Exercise? = null,
)
