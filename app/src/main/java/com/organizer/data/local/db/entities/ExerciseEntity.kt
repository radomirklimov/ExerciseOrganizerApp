package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    "exercise",
    indices = [
        Index("name", unique = true),
        Index("isCustom"),
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
)
