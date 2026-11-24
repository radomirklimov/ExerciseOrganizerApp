package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.organizer.auth.OwnerProvider
import java.util.UUID

@Entity(
    tableName = "scheduled_workout",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["workoutId"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("workoutId"), Index("syncId", unique = true), Index("ownerFirebaseUid")],
)
data class ScheduledWorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val scheduleId: Long = 0L,
    val workoutId: Long,
    val dayOfWeek: Int,
    val scheduledDateKey: Int? = null,
    val startMinutes: Int,
    val reminderEnabled: Boolean = true,
    val reminderMinutesBefore: Int = 30,
    val reminderNote: String = "",
    val ownerFirebaseUid: String = OwnerProvider.GUEST,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val serverVersion: Long = 0,
)