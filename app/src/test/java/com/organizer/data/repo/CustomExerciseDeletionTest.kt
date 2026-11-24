package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.media.MediaDownloadQueue
import com.organizer.data.remote.repo.ExerciseRemoteDataSource
import com.organizer.fakes.FakeApiService
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeFileRemote
import com.organizer.fakes.FakeFileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CustomExerciseDeletionTest {

    private fun exercise(
        id: Long,
        owner: String,
        custom: Boolean = true,
        syncId: String? = "sync-$id",
    ) = ExerciseEntity(
        exerciseId = id,
        name = "Exercise $id",
        instructions = listOf("do it"),
        imageUrls = listOf("img$id.jpg"),
        isCustom = custom,
        ownerFirebaseUid = owner,
        syncId = syncId,
    )

    private fun repository(dao: FakeExerciseDao, scope: kotlinx.coroutines.test.TestScope): ExerciseRepository {
        val store = FakeFileStore()
        val remote = FakeFileRemote()
        val queue = MediaDownloadQueue(store, remote, scope.backgroundScope, 3)
        return ExerciseRepository(dao, ExerciseRemoteDataSource(FakeApiService()), FileRepository(store, queue), OwnerProvider())
    }

    @Test
    fun `deleting custom exercise soft deletes and preserves metadata`() = runTest {
        val dao = FakeExerciseDao()
        val repo = repository(dao, this)
        dao.seed(exercise(1, OwnerProvider.GUEST))

        repo.deleteCustomExercise(1)

        val stored = dao.data.value.single()
        assertNotNull(stored.deletedAt)
        assertEquals("sync-1", stored.syncId)
        assertEquals(OwnerProvider.GUEST, stored.ownerFirebaseUid)
        assertEquals("Exercise 1", stored.name)
        assertEquals("PENDING_DELETE", stored.syncStatus)
        assertNull(repo.observeCustomExercises().first().firstOrNull())
        assertNotNull(dao.getById(OwnerProvider.GUEST, 1).first())
    }

    @Test
    fun `deleting custom exercise is idempotent`() = runTest {
        val dao = FakeExerciseDao()
        val repo = repository(dao, this)
        dao.seed(exercise(1, OwnerProvider.GUEST))

        repo.deleteCustomExercise(1)
        val firstDeletedAt = dao.data.value.single().deletedAt
        repo.deleteCustomExercise(1)

        assertEquals(firstDeletedAt, dao.data.value.single().deletedAt)
    }

    @Test
    fun `guest cannot delete another owner's custom exercise`() = runTest {
        val dao = FakeExerciseDao()
        val repo = repository(dao, this)
        dao.seed(exercise(2, "user-a"))

        repo.deleteCustomExercise(2)
        assertNull(dao.data.value.single().deletedAt)

        assertEquals(1, dao.softDeleteCustom("user-a", 2, 100L, 100L))
        assertNotNull(dao.data.value.single().deletedAt)
    }

    @Test
    fun `custom deletion path does not delete public exercises`() = runTest {
        val dao = FakeExerciseDao()
        val repo = repository(dao, this)
        dao.seed(exercise(3, "public", custom = false, syncId = null))

        repo.deleteCustomExercise(3)

        assertNull(dao.data.value.single().deletedAt)
    }
}