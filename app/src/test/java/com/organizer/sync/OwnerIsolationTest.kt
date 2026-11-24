package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import com.organizer.fakes.FakeFavoriteExerciseDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerIsolationTest {

    @Test
    fun `favorite queries are isolated by owner`() = runTest {
        val dao = FakeFavoriteExerciseDao()
        dao.insert(FavoriteExerciseEntity(exerciseId = 1L, ownerFirebaseUid = OwnerProvider.GUEST))
        dao.insert(FavoriteExerciseEntity(exerciseId = 2L, ownerFirebaseUid = "user-a"))

        assertEquals(listOf(1L), dao.observeFavoriteIds(OwnerProvider.GUEST).first())
        assertEquals(listOf(2L), dao.observeFavoriteIds("user-a").first())
        assertFalse(dao.isFavorite("user-a", 1L))
        assertTrue(dao.isFavorite("user-a", 2L))
    }

    @Test
    fun `soft delete affects only the requested owner`() = runTest {
        val dao = FakeFavoriteExerciseDao()
        dao.insert(FavoriteExerciseEntity(exerciseId = 1L, ownerFirebaseUid = OwnerProvider.GUEST))
        dao.insert(FavoriteExerciseEntity(exerciseId = 1L, ownerFirebaseUid = "user-a"))

        dao.softDelete("user-a", 1L, 123L)

        assertTrue(dao.isFavorite(OwnerProvider.GUEST, 1L))
        assertFalse(dao.isFavorite("user-a", 1L))
    }
}