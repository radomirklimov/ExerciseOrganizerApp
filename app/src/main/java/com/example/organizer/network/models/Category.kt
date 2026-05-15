package com.example.organizer.network.models

import com.example.organizer.local.entity.CategoryEntity
import kotlinx.serialization.Serializable

@Serializable
data class Category (
    val categoryId: Long,
    val name: String,
    val parentCategory: Category?,
)

fun Category.asEntity() = CategoryEntity(
    categoryId = categoryId,
    name = name,
    parentCategoryId = parentCategory?.categoryId
)
