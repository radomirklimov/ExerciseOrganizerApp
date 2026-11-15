package com.organizer.data.repo

import com.organizer.data.local.db.entities.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseCategoryLabelTest {

    private val gym = CategoryEntity(
        categoryId = 1,
        name = "Gym",
        iconUrl = null,
        parentCategoryId = null,
        sortOrder = 0,
    )
    private val home = CategoryEntity(
        categoryId = 2,
        name = "Home Workout",
        iconUrl = null,
        parentCategoryId = null,
        sortOrder = 1,
    )
    private val chest = CategoryEntity(
        categoryId = 10,
        name = "Chest",
        iconUrl = null,
        parentCategoryId = gym.categoryId,
        sortOrder = 0,
    )
    private val upperChest = CategoryEntity(
        categoryId = 11,
        name = "Upper Chest",
        iconUrl = null,
        parentCategoryId = chest.categoryId,
        sortOrder = 0,
    )
    private val triceps = CategoryEntity(
        categoryId = 12,
        name = "Triceps",
        iconUrl = null,
        parentCategoryId = gym.categoryId,
        sortOrder = 1,
    )
    private val shoulders = CategoryEntity(
        categoryId = 13,
        name = "Shoulders",
        iconUrl = null,
        parentCategoryId = gym.categoryId,
        sortOrder = 2,
    )
    private val fullBody = CategoryEntity(
        categoryId = 20,
        name = "Full Body",
        iconUrl = null,
        parentCategoryId = home.categoryId,
        sortOrder = 0,
    )

    private val all = listOf(gym, home, chest, upperChest, triceps, shoulders, fullBody)

    @Test
    fun `deep subcategory is collapsed to its broadest non-root category`() {
        val names = exerciseCategoryNames(
            linkedCategories = listOf(upperChest),
            allCategories = all,
        )

        assertEquals(listOf("Chest"), names)
    }

    @Test
    fun `two categories are joined with an ampersand`() {
        val names = exerciseCategoryNames(
            linkedCategories = listOf(upperChest, triceps),
            allCategories = all,
        )

        assertEquals(listOf("Chest", "Triceps"), names)
        assertEquals("Chest & Triceps", joinCategoryNames(names))
    }

    @Test
    fun `three or more categories are joined with a middle dot`() {
        val names = exerciseCategoryNames(
            linkedCategories = listOf(chest, triceps, shoulders),
            allCategories = all,
        )

        assertEquals("Chest · Triceps · Shoulders", joinCategoryNames(names))
    }

    @Test
    fun `only categories from the primary sport are shown`() {
        val names = exerciseCategoryNames(
            linkedCategories = listOf(fullBody, chest),
            allCategories = all,
        )

        assertEquals(listOf("Chest"), names)
    }

    @Test
    fun `a root category does not produce a label`() {
        val names = exerciseCategoryNames(
            linkedCategories = listOf(gym),
            allCategories = all,
        )

        assertEquals(emptyList<String>(), names)
        assertNull(joinCategoryNames(names))
    }

    @Test
    fun `an exercise without categories has no label`() {
        val names = exerciseCategoryNames(
            linkedCategories = emptyList(),
            allCategories = all,
        )

        assertEquals(emptyList<String>(), names)
        assertNull(joinCategoryNames(names))
    }
}
