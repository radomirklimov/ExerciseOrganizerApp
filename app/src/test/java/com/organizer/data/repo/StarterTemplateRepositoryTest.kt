package com.organizer.data.repo

import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.local.db.entities.StarterTemplateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StarterTemplateRepositoryTest {

    private val categories = listOf(
        CategoryEntity(categoryId = 1, name = "Gym", iconUrl = "gym.png", parentCategoryId = null),
        CategoryEntity(categoryId = 4, name = "Home Workout", iconUrl = "home.png", parentCategoryId = null),
        CategoryEntity(categoryId = 5, name = "Chest", iconUrl = null, parentCategoryId = 1),
        CategoryEntity(categoryId = 6, name = "Back", iconUrl = null, parentCategoryId = 1),
        CategoryEntity(categoryId = 8, name = "Arms", iconUrl = null, parentCategoryId = 1),
        CategoryEntity(categoryId = 9, name = "Legs", iconUrl = null, parentCategoryId = 1),
        CategoryEntity(categoryId = 15, name = "Dumbbells", iconUrl = null, parentCategoryId = 5),
        CategoryEntity(categoryId = 14, name = "Barbell", iconUrl = null, parentCategoryId = 5),
        CategoryEntity(categoryId = 20, name = "Biceps", iconUrl = null, parentCategoryId = 8),
        CategoryEntity(categoryId = 74, name = "Cable", iconUrl = null, parentCategoryId = 20),
        CategoryEntity(categoryId = 109, name = "Dumbbells", iconUrl = null, parentCategoryId = 4),
        CategoryEntity(categoryId = 47, name = "Lunges", iconUrl = null, parentCategoryId = 9),
        CategoryEntity(categoryId = 107, name = "No Equipment", iconUrl = null, parentCategoryId = 4),
        CategoryEntity(categoryId = 118, name = "Lower Body", iconUrl = null, parentCategoryId = 107),
        CategoryEntity(categoryId = 120, name = "Strength", iconUrl = null, parentCategoryId = 118),
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

    @Test
    fun `used categories lists every muscle group ordered by coverage`() {
        val links = mapOf(
            1L to listOf(link(1, 14)), // Chest
            2L to listOf(link(2, 14)), // Chest
            3L to listOf(link(3, 14)), // Chest
            4L to listOf(link(4, 74)), // Cable -> Biceps -> Arms
        )

        val result = usedCategories(listOf(1, 2, 3, 4), links, categories)

        assertEquals(listOf("Chest", "Arms"), result)
    }

    @Test
    fun `used categories prefers the muscle group over equipment duplicates`() {
        val links = mapOf(
            1L to listOf(link(1, 15), link(1, 109)), // Chest vs Home Workout dumbbells
        )

        val result = usedCategories(listOf(1), links, categories)

        assertEquals(listOf("Chest"), result)
    }

    @Test
    fun `used categories prefers the muscle group over a deeper equipment branch`() {
        val links = mapOf(
            1L to listOf(link(1, 47), link(1, 120)), // Legs vs Strength/Home Workout
        )

        val result = usedCategories(listOf(1), links, categories)

        assertEquals(listOf("Legs"), result)
    }

    @Test
    fun `category label joins all used categories`() {
        val two = StarterTemplateEntity(
            templateId = 1,
            name = "Push Day",
            categoryNames = listOf("Chest", "Arms"),
        )
        val three = two.copy(categoryNames = listOf("Chest", "Shoulders", "Arms"))

        assertEquals("Chest & Arms", two.categoryLabel)
        assertEquals("Chest · Shoulders · Arms", three.categoryLabel)
    }

    @Test
    fun `template without exercices uses no categories`() {
        assertEquals(emptyList<String>(), usedCategories(emptyList(), emptyMap(), categories))
    }

    private fun link(exerciseId: Long, categoryId: Long) = ExerciseCategoryEntity(
        exerciseCategoryId = exerciseId * 100 + categoryId,
        exerciseId = exerciseId,
        categoryId = categoryId,
    )
}
