package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_progress",
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
        Index("exerciseId"),
        Index("recordedAt"),
        Index("sessionId"),
    ]
)
data class ExerciseProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val progressId: Long = 0L,
    val workoutId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val weight: Double? = null,
    val durationSeconds: Int? = null,
    val recordedAt: Long = System.currentTimeMillis(),
    /**
     * One workout execution. Sets are numbered and shown per session so a new
     * session starts again at "Set 1" instead of continuing last week's list.
     * `0` marks rows recorded before sessions were introduced.
     */
    val sessionId: Long = 0L,
)