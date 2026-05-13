package com.example.organizer.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity
data class CategoryEntity(
    @PrimaryKey
    var categoryId: String = UUID.randomUUID().toString(),
    var name: String,
    var parentCategory: String,
)
