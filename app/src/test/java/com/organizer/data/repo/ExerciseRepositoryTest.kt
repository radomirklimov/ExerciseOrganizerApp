package com.organizer.data.repo

import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.media.MediaDownloadQueue
import com.organizer.data.remote.model.Exercise
import com.organizer.data.remote.repo.ExerciseRemoteDataSource
import com.organizer.fakes.FakeApiService
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeFileRemote
import com.organizer.fakes.FakeFileStore
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExerciseRepositoryTest {

    private lateinit var api: FakeApiService
    private lateinit var dao: FakeExerciseDao
    private lateinit var store: FakeFileStore
    private lateinit var remote: FakeFileRemote
    private lateinit var files: FileRepository

    @Before
    fun setUp() {
        api = FakeApiService()
        dao = FakeExerciseDao()
        store = FakeFileStore()
        remote = FakeFileRemote()
    }

    private fun repo(scope: kotlinx.coroutines.test.TestScope): ExerciseRepository {
        val queue = MediaDownloadQueue(store, remote, scope.backgroundScope, 3)
        files = FileRepository(store, queue)
        return ExerciseRepository(dao, ExerciseRemoteDataSource(api), files)
    }

    private fun exercise(
        id: Long,
        name: String = "Ex $id",
        images: List<String> = listOf("img$id.webp"),
        custom: Boolean = false,
    ) = ExerciseEntity(
        exerciseId = id,
        name = name,
        instructions = listOf("do it"),
        imageUrls = images,
        isCustom = custom,
    )

    @Test
    fun insertUpdateDeleteKeepsCustomsAndImages() = runTest {
        val repo = repo(this)
        dao.seed(
            exercise(1, "Old name", listOf("old.webp")),
            exercise(2, "Gone", listOf("gone.webp")),
            exercise(900, "Mine", listOf("mine.jpg"), custom = true),
        )
        store.preload("images", "old.webp")
        store.preload("images", "gone.webp")
        store.preload("images", "mine.jpg")
        remote.files["images/new.webp"] = byteArrayOf(1)
        remote.files["images/fresh.webp"] = byteArrayOf(2)
        api.exercises = listOf(
            Exercise(1, "New name", listOf("do it"), listOf("new.webp")),
            Exercise(3, "Fresh", listOf("do it"), listOf("fresh.webp")),
        )

        repo.refreshExercises()
        runCurrent()
        advanceUntilIdle()

        // Data: updated, inserted, deleted — custom survives untouched.
        assertEquals("New name", dao.getByIdOnce(1)?.name)
        assertEquals("Fresh", dao.getByIdOnce(3)?.name)
        assertNull(dao.getByIdOnce(2))
        assertEquals("Mine", dao.getByIdOnce(900)?.name)
        // Files: obsolete images removed, custom image kept.
        assertTrue(!store.exists("images", "old.webp"))
        assertTrue(!store.exists("images", "gone.webp"))
        assertTrue(store.exists("images", "mine.jpg"))
        // New images downloaded in background; custom image never queued.
        assertTrue(store.exists("images", "new.webp"))
        assertTrue(store.exists("images", "fresh.webp"))
        assertTrue(remote.attempts.none { it.endsWith("mine.jpg") })
    }

    @Test
    fun sharedImageIsKeptWhenOneExerciseIsDeleted() = runTest {
        val repo = repo(this)
        dao.seed(
            exercise(1, "One", listOf("shared.webp")),
            exercise(2, "Two", listOf("shared.webp")),
        )
        store.preload("images", "shared.webp")
        api.exercises = listOf(
            Exercise(2, "Two", listOf("do it"), listOf("shared.webp")),
        )

        repo.refreshExercises()
        runCurrent()
        advanceUntilIdle()

        assertNull(dao.getByIdOnce(1))
        assertTrue(store.exists("images", "shared.webp"))
        // Already cached: no download attempted.
        assertTrue(remote.attempts.isEmpty())
    }

    @Test
    fun mediaFailureDoesNotFailDataSync() = runTest {
        val repo = repo(this)
        dao.seed(exercise(1, "Old", listOf("old.webp")))
        remote.failures.add("images/new.webp")
        api.exercises = listOf(
            Exercise(1, "New", listOf("do it"), listOf("new.webp")),
        )

        // Must not throw even though the image download fails.
        repo.refreshExercises()
        runCurrent()
        advanceUntilIdle()

        assertEquals("New", dao.getByIdOnce(1)?.name)
        assertTrue(!store.exists("images", "new.webp"))
        // Retryable later: a re-request downloads once the failure is gone.
        remote.failures.remove("images/new.webp")
        remote.files["images/new.webp"] = byteArrayOf(5)
        repo.enqueueMissingImages()
        runCurrent()
        advanceUntilIdle()
        assertTrue(store.exists("images", "new.webp"))
    }

    @Test
    fun dataSyncCompletesWithoutWaitingForMedia() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        remote.gates["images/big.webp"] = gate
        remote.files["images/big.webp"] = byteArrayOf(9)
        api.exercises = listOf(
            Exercise(1, "Big", listOf("do it"), listOf("big.webp")),
        )
        val repo = repo(this)

        repo.refreshExercises()

        assertEquals("Big", dao.getByIdOnce(1)?.name)
        assertTrue(!store.exists("images", "big.webp"))

        gate.complete(Unit)
        runCurrent()
        advanceUntilIdle()
        assertTrue(store.exists("images", "big.webp"))
    }
}
