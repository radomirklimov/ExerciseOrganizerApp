package com.organizer.presentation.screens.workout

import com.organizer.presentation.model.ExerciseCatalogEntry

/** The four exercise areas the Create Workout search can operate on. */
internal enum class ExerciseFilter {
    All,
    Standard,
    Favorites,
    Custom,
}

/**
 * Applies the selected scope and search text to [entries].
 *
 * Scopes only refine search results. When [query] is blank the input order is
 * returned unchanged so the suggested exercises are shown regardless of the
 * selected scope. With a query, name matches come before category-only matches
 * and are then ordered alphabetically.
 */
internal fun filterExerciseEntries(
    entries: List<ExerciseCatalogEntry>,
    query: String,
    filter: ExerciseFilter,
    favoriteIds: Set<Long> = emptySet(),
): List<ExerciseCatalogEntry> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return entries

    return entries
        .filter { entry ->
            entry.matches(filter, favoriteIds) && entry.matches(trimmed)
        }
        .sortedWith(
            compareBy<ExerciseCatalogEntry> {
                when {
                    it.exercise.name.startsWith(trimmed, ignoreCase = true) -> 0
                    it.exercise.name.contains(trimmed, ignoreCase = true) -> 1
                    else -> 2
                }
            }.thenBy { it.exercise.name.lowercase() }
        )
}

private fun ExerciseCatalogEntry.matches(
    filter: ExerciseFilter,
    favoriteIds: Set<Long>,
): Boolean = when (filter) {
    ExerciseFilter.All -> true
    ExerciseFilter.Standard -> !exercise.isCustom
    ExerciseFilter.Favorites -> exercise.exerciseId in favoriteIds
    ExerciseFilter.Custom -> exercise.isCustom
}

private fun ExerciseCatalogEntry.matches(query: String): Boolean =
    exercise.name.contains(query, ignoreCase = true) ||
            categoryLabel?.contains(query, ignoreCase = true) == true
