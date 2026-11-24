package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.fakes.FakeFavoriteExerciseDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteExerciseRepositoryTest {

    private val owner = OwnerProvider()
    private val dao = FakeFavoriteExerciseDao()
    private val repo = FavoriteExerciseRepository(dao, owner)

    @Test
    fun `toggle likes an exercise and unlikes it again`() = runTest {
        repo.toggleFavorite(7)
        assertTrue(dao.isFavorite(OwnerProvider.GUEST, 7))
        assertEquals(listOf(7L), dao.favorites.value.map { it.exerciseId })

        repo.toggleFavorite(7)
        assertFalse(dao.isFavorite(OwnerProvider.GUEST, 7))
        assertTrue(dao.favorites.value.all { it.deletedAt != null })
    }

    @Test
    fun `toggle keeps other favorites untouched`() = runTest {
        repo.addFavorite(1)
        repo.toggleFavorite(2)
        repo.toggleFavorite(1)

        assertEquals(listOf(2L), dao.observeFavoriteIds(OwnerProvider.GUEST).first())
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