package com.organizer.data.mapper

import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.remote.model.Category
import com.organizer.data.remote.model.Exercise
import com.organizer.data.remote.model.ExerciseCategory

fun Category.asEntity() = CategoryEntity(
    categoryId = categoryId,
    name = name,
    iconUrl = iconUrl,
    parentCategoryId = parentCategoryId,
    sortOrder = sortOrder,
)

fun Exercise.asEntity() = ExerciseEntity(
    exerciseId = exerciseId,
    name = name,
    instructions = instructions,
    imageUrls = images,
    isCustom = false,
)

fun ExerciseCategory.asEntity() = ExerciseCategoryEntity(
    exerciseCategoryId = exerciseCategoryId,
    exerciseId = exerciseId,
    categoryId = categoryId,
    sortOrder = sortOrder,
)
