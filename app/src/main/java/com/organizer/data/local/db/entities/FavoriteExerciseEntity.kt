package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.organizer.auth.OwnerProvider
import java.util.UUID

@Entity(
    tableName = "favorite_exercise",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("syncId", unique = true),
        Index("ownerFirebaseUid"),
        Index(value = ["ownerFirebaseUid", "exerciseId"], unique = true),
    ]
)
data class FavoriteExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val favoriteId: Long = 0L,
    val exerciseId: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val ownerFirebaseUid: String = OwnerProvider.GUEST,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val serverVersion: Long = 0,
)