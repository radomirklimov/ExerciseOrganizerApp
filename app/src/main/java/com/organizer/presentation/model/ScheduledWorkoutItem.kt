package com.organizer.presentation.model

import com.organizer.data.local.db.entities.ScheduledWorkoutEntity

/** A schedule entry joined with the workout information the card shows. */
data class ScheduledWorkoutItem(
    val entry: ScheduledWorkoutEntity,
    val workoutName: String,
    val exerciseCount: Int,
    val categories: List<String>,
)
