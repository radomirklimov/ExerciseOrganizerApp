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

/** Maps a catalog DTO with a locally allocated Room PK and a stable catalog ID. */
fun Exercise.asEntity() = ExerciseEntity(
    exerciseId = 0L,
    publicExerciseId = exerciseId,
    name = name,
    instructions = instructions,
    imageUrls = images,
    isCustom = false,
    exerciseType = exerciseType,
)

fun ExerciseCategory.asEntity() = ExerciseCategoryEntity(
    exerciseCategoryId = exerciseCategoryId,
    exerciseId = exerciseId,
    categoryId = categoryId,
    sortOrder = sortOrder,
)
