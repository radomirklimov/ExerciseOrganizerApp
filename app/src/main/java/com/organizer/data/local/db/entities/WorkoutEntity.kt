package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.organizer.auth.OwnerProvider
import java.util.UUID

@Entity(
    "workout",
    indices = [
        Index(value = ["ownerFirebaseUid", "normalizedName"], unique = true),
        Index("syncId", unique = true),
        Index("ownerFirebaseUid"),
    ]
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val workoutId: Long = 0L,
    val name: String,
    val notes: String = "",
    val ownerFirebaseUid: String = OwnerProvider.GUEST,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val serverVersion: Long = 0,
    /** Null for tombstones; active rows store trimmed lowercase name. */
    val normalizedName: String? = null,
)