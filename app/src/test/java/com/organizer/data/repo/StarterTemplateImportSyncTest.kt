package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.StarterTemplateDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.data.local.db.entities.StarterTemplateExerciseEntity
import com.organizer.data.media.MediaDownloadQueue
import com.organizer.data.remote.ApiService
import com.organizer.data.remote.model.WorkoutTemplate
import com.organizer.data.remote.repo.StarterTemplateRemoteDataSource
import com.organizer.fakes.FakeApiService
import com.organizer.fakes.FakeCategoryDao
import com.organizer.fakes.FakeExerciseCategoryDao
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeFileRemote
import com.organizer.fakes.FakeFileStore
import com.organizer.fakes.FakeSyncOutboxDao
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.FakeWorkoutExerciseDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import com.organizer.sync.SyncEntityTypes
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncOutboxStatus
import com.organizer.sync.SyncPayloadFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StarterTemplateImportSyncTest {
    private class FakeTemplateDao : StarterTemplateDao {
        var template = StarterTemplateEntity(templateId = 1L, name = "Template", exerciseCount = 2)
        var exerciseIds = listOf(10L, 11L)
        override suspend fun getByIdOnce(id: Long): StarterTemplateEntity? = template.takeIf { it.templateId == id }
        override suspend fun getExerciseIdsOnce(templateId: Long): List<Long> = exerciseIds
        override fun observeAll() = throw UnsupportedOperationException()
        override fun observeById(id: Long) = throw UnsupportedOperationException()
        override fun observeExercises(templateId: Long) = throw UnsupportedOperationException()
        override suspend fun insertTemplates(templates: List<StarterTemplateEntity>) = Unit
        override suspend fun insertExercises(exercises: List<StarterTemplateExerciseEntity>) = Unit
        override suspend fun deleteExercisesForTemplate(templateId: Long) = Unit
        override suspend fun deleteAllExercises() = Unit
        override suspend fun deleteAllTemplates() = Unit
        override suspend fun replaceAll(templates: List<StarterTemplateEntity>, exercises: List<StarterTemplateExerciseEntity>) = Unit
    }

    @Test
    fun `template import creates ordered associations and outbox mutations`() = runTest {
        val owner = object : OwnerProvider() { override fun currentOwner() = "user-a" }
        val templateDao = FakeTemplateDao()
        val outbox = FakeSyncOutboxDao()
        val json = Json { ignoreUnknownKeys = true }
        val workouts = FakeWorkoutDao()
        val links = FakeWorkoutExerciseDao()
        val exercises = FakeExerciseDao()
        val transactions = ImmediateSyncTransactionRunner()

        exercises.seed(
            ExerciseEntity(exerciseId = 10L, name = "E1", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 100),
            ExerciseEntity(exerciseId = 11L, name = "E2", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, ownerFirebaseUid = "public", publicExerciseId = 101),
        )
        val workoutRepo = WorkoutRepository(workouts, owner, transactions, fakeOutboxWriter(outbox).first)
        val associationRepo = WorkoutExerciseRepository(
            links, workouts, exercises, outbox, com.organizer.fakes.FakeSyncStagedDao(), owner, transactions,
            fakeOutboxWriter(outbox).first, SyncPayloadFactory(json), SyncOperationLocks(),
        )
        val fileStore = FakeFileStore()
        val mediaQueue = MediaDownloadQueue(fileStore, FakeFileRemote(), TestScope())
        val repo = StarterTemplateRepository(
            templateDao, StarterTemplateRemoteDataSource(FakeApiService()),
            FakeCategoryDao(), FakeExerciseCategoryDao(), exercises, workouts,
            workoutRepo, associationRepo, links, FileRepository(fileStore, mediaQueue), owner,
        )

        val workoutId = repo.importTemplate(1L)!!
        val stored = links.data.value.sortedBy { it.orderIndex }
        assertEquals(2, stored.size)
        assertEquals(listOf(0, 1), stored.map { it.orderIndex })
        assertEquals("user-a", stored.first().ownerFirebaseUid)
        assertNotNull(stored.first().syncId)

        val mutations = outbox.entries.value.filter { it.entityType == SyncEntityTypes.WORKOUT_EXERCISE }
        assertEquals(2, mutations.size)
        assertEquals(stored.map { it.syncId }.toSet(), mutations.map { it.recordSyncId }.toSet())
        assertEquals(SyncOutboxStatus.PENDING_UPLOAD, mutations.first().syncStatus)
        assertEquals(workoutId, stored.first().workoutId)
    }
}
