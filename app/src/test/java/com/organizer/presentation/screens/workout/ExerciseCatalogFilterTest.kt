package com.organizer.presentation.screens.workout

import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.presentation.model.ExerciseCatalogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseCatalogFilterTest {

    private val bench = entry(1, "Bench Press", "Chest", isCustom = false)
    private val incline = entry(2, "Incline Bench Press", "Chest & Shoulders")
    private val dips = entry(3, "Dips", "Chest & Arms")
    private val squat = entry(4, "Barbell Squat", "Legs")
    private val myBench = entry(5, "My Bench Variation", "Chest", isCustom = true)
    private val myStretch = entry(6, "My Stretch Routine", "Mobility", isCustom = true)

    @Test
    fun `blank query keeps suggestions in usage order`() {
        val result = filterExerciseEntries(
            entries = listOf(dips, bench, squat),
            query = "",
            filter = ExerciseFilter.All,
            favoriteIds = emptySet(),
        )

        assertEquals(listOf(3L, 1L, 4L), result.map { it.exercise.exerciseId })
    }

    @Test
    fun `all filter searches standard and custom exercises`() {
        val result = filterExerciseEntries(
            entries = listOf(bench, myBench, myStretch, squat),
            query = "bench",
            filter = ExerciseFilter.All,
            favoriteIds = emptySet(),
        )

        assertEquals(listOf(1L, 5L), result.map { it.exercise.exerciseId })
    }

    @Test
    fun `standard filter excludes custom exercises`() {
        val result = filterExerciseEntries(
            entries = listOf(bench, myBench, myStretch, squat),
            query = "bench",
            filter = ExerciseFilter.Standard,
            favoriteIds = emptySet(),
        )

        assertEquals(listOf(1L), result.map { it.exercise.exerciseId })
    }

    @Test
    fun `custom filter excludes standard exercises`() {
        val result = filterExerciseEntries(
            entries = listOf(bench, myBench, myStretch, squat),
            query = "my",
            filter = ExerciseFilter.Custom,
            favoriteIds = emptySet(),
        )

        assertEquals(listOf(5L, 6L), result.map { it.exercise.exerciseId })
    }

    @Test
    fun `blank query ignores the selected scope`() {
        val entries = listOf(dips, bench, squat, myBench)

        ExerciseFilter.entries.forEach { filter ->
            val result = filterExerciseEntries(
                entries = entries,
                query = "",
                filter = filter,
                favoriteIds = setOf(4L),
            )

            assertEquals(
                "scope $filter must not filter suggestions",
                listOf(3L, 1L, 4L, 5L),
                result.map { it.exercise.exerciseId },
            )
        }
    }

    @Test
    fun `favorites filter uses favorite ids for a search`() {
        val result = filterExerciseEntries(
            entries = listOf(bench, incline, dips, squat),
            query = "press",
            filter = ExerciseFilter.Favorites,
            favoriteIds = setOf(2L, 4L),
        )

        assertEquals(listOf(2L), result.map { it.exercise.exerciseId })
    }

    @Test
    fun `favorites filter also applies to the search text`() {
        val result = filterExerciseEntries(
            entries = listOf(bench, incline, dips, squat),
            query = "bench",
            filter = ExerciseFilter.Favorites,
            favoriteIds = setOf(2L, 4L),
        )

        assertEquals(listOf(2L), result.map { it.exercise.exerciseId })
    }

    @Test
    fun `name matches are ranked before category-only matches`() {
        val chestFly = entry(7, "Chest Fly", "Chest")
        val cableRow = entry(8, "Cable Row", "Chest")

        val result = filterExerciseEntries(
            entries = listOf(cableRow, chestFly),
            query = "chest",
            filter = ExerciseFilter.All,
            favoriteIds = emptySet(),
        )

        assertEquals(listOf(7L, 8L), result.map { it.exercise.exerciseId })
    }

    private fun entry(
        id: Long,
        name: String,
        categoryLabel: String?,
        isCustom: Boolean = false,
    ) = ExerciseCatalogEntry(
        exercise = ExerciseEntity(
            exerciseId = id,
            name = name,
            instructions = emptyList(),
            imageUrls = emptyList(),
            isCustom = isCustom,
        ),
        categoryLabel = categoryLabel,
    )
}
