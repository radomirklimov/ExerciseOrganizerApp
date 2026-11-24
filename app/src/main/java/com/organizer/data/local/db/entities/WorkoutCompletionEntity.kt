package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.organizer.auth.OwnerProvider
import java.util.UUID

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
    indices = [Index("workoutId"), Index("syncId", unique = true), Index("ownerFirebaseUid")]
)
data class WorkoutCompletionEntity(
    val dateKey: Int,
    val workoutId: Long,
    val completedExercises: Int,
    val totalExercises: Int,
    val completedAt: Long = System.currentTimeMillis(),
    val ownerFirebaseUid: String = OwnerProvider.GUEST,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val serverVersion: Long = 0,
)