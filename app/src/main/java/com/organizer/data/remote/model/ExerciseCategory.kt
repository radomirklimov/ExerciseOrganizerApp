package com.organizer.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseCategory (
    val exerciseCategoryId: Long,
    val exerciseId: Long,
    val categoryId: Long,
    val sortOrder: Int = 0,
)
