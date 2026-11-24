package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    "exercise",
    indices = [
        Index("name", unique = true),
        Index("isCustom"),
        Index("syncId", unique = true),
        Index("ownerFirebaseUid"),
    ]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val exerciseId: Long = 0L,
    val name: String,
    val instructions: List<String>,
    val imageUrls: List<String>,
    val isCustom: Boolean,
    val exerciseType: String = "REPS_WEIGHT",
    val ownerFirebaseUid: String = "public",
    /** Null for public/cached exercises; UUID for user-owned custom exercises. */
    val syncId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val serverVersion: Long = 0,
)