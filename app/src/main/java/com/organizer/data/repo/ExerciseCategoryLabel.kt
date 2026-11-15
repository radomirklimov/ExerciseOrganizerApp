package com.organizer.data.repo

import com.organizer.data.local.db.entities.CategoryEntity

/**
 * Builds a short category summary for a single exercise, e.g. "Chest & Arms".
 *
 * Exercises are often cross-listed in several sports (Gym, Home Workout, ...).
 * The primary sport (lowest sort order) wins, and inside it the broadest
 * non-root category is used, so equipment duplicates such as "Dumbbells" do
 * not hide the muscle group.
 */
internal fun exerciseCategoryNames(
    linkedCategories: List<CategoryEntity>,
    allCategories: List<CategoryEntity>,
): List<String> {
    if (linkedCategories.isEmpty()) return emptyList()

    val byId = allCategories.associateBy { it.categoryId }
    val branches = linkedCategories.mapNotNull { linked ->
        var current = byId[linked.categoryId] ?: return@mapNotNull null
        var broadestNonRoot: CategoryEntity? = null
        val visited = mutableSetOf<Long>()

        while (visited.add(current.categoryId)) {
            val parentId = current.parentCategoryId ?: break
            broadestNonRoot = current
            current = byId[parentId] ?: break
        }

        val broad = broadestNonRoot ?: return@mapNotNull null
        ExerciseCategoryBranch(name = broad.name, rootSortOrder = current.sortOrder)
    }
    if (branches.isEmpty()) return emptyList()

    val primaryRootSortOrder = branches.minOf { it.rootSortOrder }
    return branches
        .filter { it.rootSortOrder == primaryRootSortOrder }
        .map { it.name }
        .distinct()
}

/** Formats category names the same way starter templates are labelled. */
internal fun joinCategoryNames(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> names.first()
    2 -> names.joinToString(" & ")
    else -> names.joinToString(" · ")
}

private data class ExerciseCategoryBranch(
    val name: String,
    val rootSortOrder: Int,
)
