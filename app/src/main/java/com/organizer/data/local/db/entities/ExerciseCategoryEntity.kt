package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_category",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["exerciseId"]),
        Index(value = ["categoryId"]),
        Index(value = ["exerciseId", "categoryId"], unique = true)
    ]
)
data class ExerciseCategoryEntity(
    @PrimaryKey
    val exerciseCategoryId: Long,
    val exerciseId: Long,
    val categoryId: Long,
    val sortOrder: Int = 0,
)
