package com.organizer.presentation.model

import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity

sealed interface SearchResult {
    val name: String

    data class Sport(val sport: CategoryEntity) : SearchResult {
        override val name = sport.name
    }

    data class Category(val category: CategoryEntity) : SearchResult {
        override val name = category.name
    }

    data class Exercise(val exercise: ExerciseEntity) : SearchResult {
        override val name = exercise.name
    }
}
