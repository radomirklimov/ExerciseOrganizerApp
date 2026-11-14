package com.organizer.data.repo

import com.organizer.fakes.FakeFavoriteExerciseDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteExerciseRepositoryTest {

    private val dao = FakeFavoriteExerciseDao()
    private val repo = FavoriteExerciseRepository(dao)

    @Test
    fun `toggle likes an exercise and unlikes it again`() = runTest {
        repo.toggleFavorite(7)
        assertTrue(dao.isFavorite(7))
        assertEquals(listOf(7L), dao.favorites.value.map { it.exerciseId })

        repo.toggleFavorite(7)
        assertFalse(dao.isFavorite(7))
        assertTrue(dao.favorites.value.isEmpty())
    }

    @Test
    fun `toggle keeps other favorites untouched`() = runTest {
        repo.addFavorite(1)
        repo.toggleFavorite(2)
        repo.toggleFavorite(1)

        assertEquals(listOf(2L), dao.favorites.value.map { it.exerciseId })
    }

    @Test
    fun `is favorite flow follows the stored favorites`() = runTest {
        assertFalse(repo.observeIsFavorite(3).first())

        repo.addFavorite(3)
        assertTrue(repo.observeIsFavorite(3).first())

        repo.removeFavorite(3)
        assertFalse(repo.observeIsFavorite(3).first())
    }
}
