package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A target set that belongs to a workout routine, e.g. "Set 3: 80 kg x 8 reps".
 *
 * A workout is a reusable routine, so its plan lives here instead of in the
 * logged progress. Progress rows record what the user actually did in a
 * session; planned rows describe what the routine asks for.
 */
@Entity(
    tableName = "planned_set",
    primaryKeys = ["workoutId", "exerciseId", "setNumber"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["workoutId"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("workoutId"),
        Index("exerciseId")
    ]
)
data class PlannedSetEntity(
    val workoutId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val weight: Double? = null,
    val durationSeconds: Int? = null,
)
