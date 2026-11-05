package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
    ],
    indices = [
        Index("name"),
        Index("parentCategoryId"),
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val categoryId: Long,
    val name: String,
    val iconUrl: String?,
    val parentCategoryId: Long?,
    val sortOrder: Int = 0,
)
