package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * How much of a workout was performed on a given day.
 *
 * The player tracks exercises only (done / skipped), so this row stores the
 * counts instead of individual results. The calendar shows the resulting
 * percentage on the matching date.
 *
 * `dateKey` is the compact local date (`yyyyMMdd`) used by the schedule, so a
 * completion is tied to the day it happened on rather than to a specific
 * schedule entry (a workout can be scheduled more than once).
 */
@Entity(
    tableName = "workout_completion",
    primaryKeys = ["dateKey", "workoutId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["workoutId"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId")]
)
data class WorkoutCompletionEntity(
    val dateKey: Int,
    val workoutId: Long,
    val completedExercises: Int,
    val totalExercises: Int,
    val completedAt: Long = System.currentTimeMillis(),
)
