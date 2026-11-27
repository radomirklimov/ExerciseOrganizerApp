package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeFavoriteExerciseDao
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncPayloadFactory
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteExerciseRepositoryTest {

    private class TestOwner : OwnerProvider() {
        val ownerFlow = kotlinx.coroutines.flow.MutableStateFlow(OwnerProvider.GUEST)
        override fun currentOwner(): String = ownerFlow.value
        override fun observeOwner(): kotlinx.coroutines.flow.Flow<String> = ownerFlow
    }

    private val owner = TestOwner()
    private val dao = FakeFavoriteExerciseDao()
    private val exercises = FakeExerciseDao()
    private val outbox = FakeSyncOutboxDao()
    private val repo = FavoriteExerciseRepository(
        dao, exercises, outbox, owner, ImmediateSyncTransactionRunner(),
        fakeOutboxWriter(outbox).first, SyncPayloadFactory(Json { ignoreUnknownKeys = true }), SyncOperationLocks(),
    )

    private fun seedExercise(id: Long = 7L) {
        exercises.seed(
            ExerciseEntity(
                exerciseId = id, name = "Ex", instructions = emptyList(), imageUrls = emptyList(),
                isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 42,
            )
        )
    }

    @Test
    fun `toggle likes an exercise and unlikes it again`() = runTest {
        seedExercise(7)
        repo.toggleFavorite(7)
        assertTrue(dao.isFavorite(OwnerProvider.GUEST, 7))
        assertEquals(listOf(7L), dao.favorites.value.map { it.exerciseId })

        repo.toggleFavorite(7)
        assertFalse(dao.isFavorite(OwnerProvider.GUEST, 7))
        assertTrue(dao.favorites.value.all { it.deletedAt != null })
    }

    @Test
    fun `favorite observation follows account owner changes`() = runTest {
        seedExercise(7)
        owner.ownerFlow.value = "user-a"
        repo.toggleFavorite(7)
        assertEquals(true, repo.observeIsFavorite(7).first())
        assertEquals(listOf(7L), repo.observeFavoriteExerciseIds().first())

        owner.ownerFlow.value = "user-b"
        assertEquals(false, repo.observeIsFavorite(7).first())
        assertEquals(emptyList<Long>(), repo.observeFavoriteExerciseIds().first())
    }

    @Test
    fun `toggle keeps other favorites untouched`() = runTest {
        seedExercise(1)
        seedExercise(2)
        repo.addFavorite(1)
        repo.toggleFavorite(2)
        repo.toggleFavorite(1)

        assertEquals(listOf(2L), dao.observeFavoriteIds(OwnerProvider.GUEST).first())
    }

    @Test
    fun `deterministic syncId is stable for same owner and exercise`() = runTest {
        seedExercise(7)
        val id1 = repo.deterministicSyncId(OwnerProvider.GUEST, exercises.getByIdOnce(OwnerProvider.GUEST, 7L)!!)
        val id2 = repo.deterministicSyncId(OwnerProvider.GUEST, exercises.getByIdOnce(OwnerProvider.GUEST, 7L)!!)
        assertEquals(id1, id2)
        assertNotNull(id1)
    }

    @Test
    fun `deterministic syncId differs for different owners`() = runTest {
        seedExercise(7)
        val ex = exercises.getByIdOnce(OwnerProvider.GUEST, 7L)!!
        val a = repo.deterministicSyncId("user-a", ex)
        val b = repo.deterministicSyncId("user-b", ex)
        assertTrue(a != b)
    }

    @Test
    fun `invalid public exercise identity returns null`() = runTest {
        val invalid = ExerciseEntity(
            exerciseId = 99L, name = "Bad", instructions = emptyList(), imageUrls = emptyList(),
            isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 0,
        )
        assertNull(repo.deterministicSyncId(OwnerProvider.GUEST, invalid))
    }

    @Test
    fun `is favorite flow follows the stored favorites`() = runTest {
        seedExercise(3)
        assertFalse(repo.observeIsFavorite(3).first())

        repo.addFavorite(3)
        assertTrue(repo.observeIsFavorite(3).first())

        repo.removeFavorite(3)
        assertFalse(repo.observeIsFavorite(3).first())
    }
}