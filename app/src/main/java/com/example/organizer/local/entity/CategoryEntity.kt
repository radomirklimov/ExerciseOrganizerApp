package com.example.organizer.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    "category",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["parentCategoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val categoryId: Long,
    val name: String,
    val parentCategoryId: Long?,
)
