package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.organizer.auth.OwnerProvider
import java.util.UUID

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
        Index("exerciseId"),
        Index("syncId", unique = true),
        Index("ownerFirebaseUid"),
    ]
)
data class PlannedSetEntity(
    val workoutId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val weight: Double? = null,
    val durationSeconds: Int? = null,
    val ownerFirebaseUid: String = OwnerProvider.GUEST,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val serverVersion: Long = 0,
)