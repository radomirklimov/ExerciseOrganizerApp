package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    "workout",
    indices = [
        Index("name", unique = true)
    ]
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val workoutId: Long = 0L,
    val name: String,
    val notes: String = "",
)
