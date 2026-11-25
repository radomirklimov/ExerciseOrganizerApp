package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.media.MediaDownloadQueue
import com.organizer.data.remote.model.Exercise
import com.organizer.data.remote.repo.ExerciseRemoteDataSource
import com.organizer.fakes.FakeApiService
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeFileRemote
import com.organizer.fakes.FakeFileStore
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        return ExerciseRepository(
            dao,
            ExerciseRemoteDataSource(api),
            files,
            OwnerProvider(),
            ImmediateSyncTransactionRunner(),
            fakeOutboxWriter().first,
        )
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
        publicExerciseId = if (custom) null else id,
    )

    @Test
    fun refreshUpdatesInsertsAndKeepsMissingRowsAndCustoms() = runTest {
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

        // Data: updated in place, inserted locally, missing row retained — custom untouched.
        assertEquals("New name", dao.getByIdOnce(OwnerProvider.GUEST,1)?.name)
        assertEquals(1L, dao.getByIdOnce(OwnerProvider.GUEST,1)?.publicExerciseId)
        val fresh = dao.findPublicByCatalogId(3)
        assertEquals("Fresh", fresh?.name)
        assertTrue(fresh!!.exerciseId != 3L) // local PK differs from catalog ID where allocated after customs
        assertEquals(3L, fresh.publicExerciseId)
        assertNotNull(dao.getByIdOnce(OwnerProvider.GUEST,2))
        assertEquals("Mine", dao.getByIdOnce(OwnerProvider.GUEST,900)?.name)
        // Files: custom image kept; no destructive deletion of referenced images.
        assertTrue(store.exists("images", "old.webp"))
        assertTrue(store.exists("images", "gone.webp"))
        assertTrue(store.exists("images", "mine.jpg"))
        // New images downloaded in background; custom image never queued.
        assertTrue(store.exists("images", "new.webp"))
        assertTrue(store.exists("images", "fresh.webp"))
        assertTrue(remote.attempts.none { it.endsWith("mine.jpg") })
    }

    @Test
    fun sharedImageIsKeptWhenCatalogEntryIsMissing() = runTest {
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

        // The missing catalog entry is retained; no cascade can delete user data.
        assertNotNull(dao.findPublicByCatalogId(1))
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

        assertEquals("New", dao.getByIdOnce(OwnerProvider.GUEST,1)?.name)
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

        assertEquals("Big", dao.getByIdOnce(OwnerProvider.GUEST,1)?.name)
        assertTrue(!store.exists("images", "big.webp"))

        gate.complete(Unit)
        runCurrent()
        advanceUntilIdle()
        assertTrue(store.exists("images", "big.webp"))
    }

    @Test
    fun `catalog id colliding with custom local pk does not overwrite either row`() = runTest {
        val repo = repo(this)
        dao.seed(
            ExerciseEntity(
                exerciseId = 50L,
                name = "Mine",
                instructions = emptyList(),
                imageUrls = emptyList(),
                isCustom = true,
                ownerFirebaseUid = OwnerProvider.GUEST,
                syncId = "custom-sync-50",
            ),
        )
        api.exercises = listOf(Exercise(50, "Catalog 50", listOf("do it"), listOf("catalog.webp")))

        repo.refreshExercises()
        runCurrent()
        advanceUntilIdle()

        val custom = dao.getByIdOnce(OwnerProvider.GUEST, 50L)!!
        assertEquals("Mine", custom.name)
        assertEquals("custom-sync-50", custom.syncId)
        assertEquals(OwnerProvider.GUEST, custom.ownerFirebaseUid)
        assertNull(custom.publicExerciseId)
        val public = dao.getAllPublicOnce().single { it.publicExerciseId == 50L }
        assertTrue(public.exerciseId != 50L)
        assertEquals("Catalog 50", public.name)
    }

    @Test
    fun `repeated refresh is idempotent`() = runTest {
        val repo = repo(this)
        api.exercises = listOf(Exercise(7, "First", listOf("do it"), listOf("a.webp")))

        repo.refreshExercises()
        runCurrent()
        advanceUntilIdle()
        val first = dao.getAllPublicOnce().single { it.publicExerciseId == 7L }

        repo.refreshExercises()
        runCurrent()
        advanceUntilIdle()
        val rows = dao.getAllPublicOnce().filter { it.publicExerciseId == 7L }

        assertEquals(1, rows.size)
        assertEquals(first.exerciseId, rows.single().exerciseId)
    }

    @Test
    fun `catalog name colliding with a custom row does not replace the custom row`() = runTest {
        val repo = repo(this)
        dao.seed(
            ExerciseEntity(
                exerciseId = 40L,
                name = "Shared",
                instructions = emptyList(),
                imageUrls = emptyList(),
                isCustom = true,
                ownerFirebaseUid = OwnerProvider.GUEST,
                syncId = "custom-sync-40",
            ),
        )
        api.exercises = listOf(Exercise(40, "Shared", listOf("do it"), listOf("catalog.webp")))

        val result = repo.refreshExercises()
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, result.skippedNameCollision)
        assertEquals("custom-sync-40", dao.getByIdOnce(OwnerProvider.GUEST, 40L)!!.syncId)
        assertTrue(dao.getAllPublicOnce().none { it.publicExerciseId == 40L })
    }

    @Test
    fun `soft deleted or foreign custom name also blocks catalog insert`() = runTest {
        val repo = repo(this)
        dao.seed(
            ExerciseEntity(
                exerciseId = 41L,
                name = "Hidden",
                instructions = emptyList(),
                imageUrls = emptyList(),
                isCustom = true,
                ownerFirebaseUid = "another-user",
                syncId = "foreign-sync-41",
                deletedAt = 100L,
            ),
        )
        api.exercises = listOf(Exercise(41, "Hidden", listOf("do it"), listOf("catalog.webp")))

        val result = repo.refreshExercises()

        assertEquals(1, result.skippedNameCollision)
        assertEquals(0, result.inserted)
        assertEquals("foreign-sync-41", dao.data.value.single { it.exerciseId == 41L }.syncId)
    }
}
