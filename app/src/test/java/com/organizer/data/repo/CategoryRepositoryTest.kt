package com.organizer.data.repo

import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.media.MediaDownloadQueue
import com.organizer.data.remote.repo.CategoryRemoteDataSource
import com.organizer.fakes.FakeApiService
import com.organizer.fakes.FakeCategoryDao
import com.organizer.fakes.FakeFileRemote
import com.organizer.fakes.FakeFileStore
import com.organizer.data.remote.model.Category
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CategoryRepositoryTest {

    private lateinit var api: FakeApiService
    private lateinit var dao: FakeCategoryDao
    private lateinit var store: FakeFileStore
    private lateinit var remote: FakeFileRemote
    private lateinit var files: FileRepository
    private lateinit var repo: CategoryRepository

    @Before
    fun setUp() {
        api = FakeApiService()
        dao = FakeCategoryDao()
        store = FakeFileStore()
        remote = FakeFileRemote()
    }

    private fun repo(scope: kotlinx.coroutines.test.TestScope): CategoryRepository {
        val queue = MediaDownloadQueue(store, remote, scope.backgroundScope, 3)
        files = FileRepository(store, queue)
        return CategoryRepository(dao, CategoryRemoteDataSource(api), files)
    }

    @Test
    fun dataSyncCompletesWithoutWaitingForMedia() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.gates["icons/gym.png"] = gate
        remote.files["icons/gym.png"] = byteArrayOf(7)
        api.sports = listOf(Category(1, "Gym", "gym.png", null))
        val repo = repo(this)

        // refreshSports returns while the icon download is still gated.
        repo.refreshSports()

        assertEquals(listOf(CategoryEntity(1, "Gym", "gym.png", null)), dao.getSportsOnce())
        assertTrue(!store.exists("icons", "gym.png"))

        gate.complete(Unit)
        runCurrent()
        advanceUntilIdle()
        assertTrue(store.exists("icons", "gym.png"))
    }

    @Test
    fun insertUpdateDeleteWithIconHandling() = runTest {
        val repo = repo(this)
        dao.seed(
            CategoryEntity(1, "Gym old", "old.png", null),
            CategoryEntity(2, "Gone", "gone.png", null),
        )
        store.preload("icons", "old.png")
        store.preload("icons", "gone.png")
        remote.files["icons/new.png"] = byteArrayOf(1)
        api.sports = listOf(
            Category(1, "Gym", "new.png", null),
            Category(3, "Fresh", "new.png", null),
        )

        repo.refreshSports()
        runCurrent()
        advanceUntilIdle()

        val ids = dao.getSportsOnce().map { it.categoryId }.toSet()
        assertEquals(setOf(1L, 3L), ids)
        assertEquals("Gym", dao.getByIdOnce(1)?.name)
        // Removed sport's icon deleted; replaced icon deleted (unreferenced).
        assertTrue(!store.exists("icons", "gone.png"))
        assertTrue(!store.exists("icons", "old.png"))
        // New icon downloaded in background.
        assertTrue(store.exists("icons", "new.png"))
    }

    @Test
    fun iconSharedWithOtherSubsetIsKept() = runTest {
        val repo = repo(this)
        dao.seed(
            CategoryEntity(1, "Gym", "shared.png", null),
            CategoryEntity(12, "Chest", "shared.png", 1),
        )
        store.preload("icons", "shared.png")
        // Sports endpoint no longer returns sport 1, but a category still uses the icon.
        api.sports = emptyList()

        repo.refreshSports()
        runCurrent()
        advanceUntilIdle()

        assertNull(dao.getByIdOnce(1))
        assertTrue(store.exists("icons", "shared.png"))
    }

    @Test
    fun inFlightDownloadBlocksFileDeletion() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.gates["icons/gym.png"] = gate
        remote.files["icons/gym.png"] = byteArrayOf(4)
        val repo = repo(this)
        dao.seed(CategoryEntity(1, "Gym", "gym.png", null))
        api.sports = emptyList()

        // Start a download for the icon, then sync while it is in flight.
        files.enqueueIcons(listOf("gym.png"))
        repo.refreshSports()

        // Record gone from Room, but the file survives until the download finishes.
        assertNull(dao.getByIdOnce(1))
        gate.complete(Unit)
        runCurrent()
        advanceUntilIdle()
        assertTrue(store.exists("icons", "gym.png"))
    }

    @Test
    fun missingIconsAreQueuedForBackground() = runTest {
        val repo = repo(this)
        dao.seed(
            CategoryEntity(1, "Gym", "gym.png", null),
            CategoryEntity(12, "Chest", null, 1),
        )
        remote.files["icons/gym.png"] = byteArrayOf(3)

        repo.enqueueMissingIcons()
        runCurrent()
        advanceUntilIdle()

        assertTrue(store.exists("icons", "gym.png"))
        assertEquals(1, remote.attempts.size)
    }
}
