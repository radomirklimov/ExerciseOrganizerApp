package com.organizer.data.repo

import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StarterTemplateRepositoryTest {

    private val categories = listOf(
        CategoryEntity(categoryId = 1, name = "Gym", iconUrl = "gym.png", parentCategoryId = null),
        CategoryEntity(categoryId = 5, name = "Chest", iconUrl = null, parentCategoryId = 1),
        CategoryEntity(categoryId = 14, name = "Barbell", iconUrl = null, parentCategoryId = 5),
        CategoryEntity(categoryId = 6, name = "Back", iconUrl = null, parentCategoryId = 1),
        CategoryEntity(categoryId = 15, name = "Cable", iconUrl = null, parentCategoryId = 6),
    ).associateBy { it.categoryId }

    @Test
    fun `dominant category is the non-root category covering most exercises`() {
        val links = mapOf(
            1L to listOf(link(1, 14)),
            2L to listOf(link(2, 14)),
            3L to listOf(link(3, 15)),
        )

        val result = dominantCategory(listOf(1, 2, 3), links, categories)

        assertEquals("Chest", result?.name)
    }

    @Test
    fun `dominant category ignores sport roots`() {
        val links = mapOf(1L to listOf(link(1, 14)))

        val result = dominantCategory(listOf(1), links, categories)

        // Gym is a root and would always win. On equal coverage the broader
        // category (Chest) is preferred over the equipment subcategory.
        assertEquals("Chest", result?.name)
    }

    @Test
    fun `dominant category falls back to the closest icon`() {
        val links = mapOf(1L to listOf(link(1, 14)))

        val result = dominantCategory(listOf(1), links, categories)

        // Barbell and Chest have no icon; Gym provides the closest one.
        assertEquals("gym.png", result?.iconUrl)
    }

    @Test
    fun `template without exercises has no dominant category`() {
        assertNull(dominantCategory(emptyList(), emptyMap(), categories))
    }

    private fun link(exerciseId: Long, categoryId: Long) = ExerciseCategoryEntity(
        exerciseCategoryId = exerciseId * 100 + categoryId,
        exerciseId = exerciseId,
        categoryId = categoryId,
    )
}
