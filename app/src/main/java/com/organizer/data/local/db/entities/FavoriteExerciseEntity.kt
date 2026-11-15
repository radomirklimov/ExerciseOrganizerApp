package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * An exercise the user marked as favorite. Kept in its own table so library
 * syncs never overwrite the user's choice.
 */
@Entity(
    tableName = "favorite_exercise",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class FavoriteExerciseEntity(
    @PrimaryKey
    val exerciseId: Long,
    /** Timestamp used to show the most recently liked exercises first. */
    val addedAt: Long = System.currentTimeMillis(),
)
