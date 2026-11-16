package com.organizer.presentation.model

import com.organizer.data.local.db.entities.ExerciseEntity

/**
 * An exercise plus the short category summary shown under its name.
 */
data class ExerciseCatalogEntry(
    val exercise: ExerciseEntity,
    val categoryLabel: String?,
)
