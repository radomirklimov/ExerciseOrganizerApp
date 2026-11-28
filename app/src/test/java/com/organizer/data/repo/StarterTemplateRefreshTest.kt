package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.StarterTemplateDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.data.local.db.entities.StarterTemplateExerciseEntity
import com.organizer.data.media.MediaDownloadQueue
import com.organizer.data.remote.model.Exercise
import com.organizer.data.remote.model.WorkoutTemplate
import com.organizer.data.remote.model.WorkoutTemplateExercise
import com.organizer.data.remote.repo.StarterTemplateRemoteDataSource
import com.organizer.fakes.FakeApiService
import com.organizer.fakes.FakeCategoryDao
import com.organizer.fakes.FakeExerciseCategoryDao
import com.organizer.fakes.FakeExerciseDao
import com.organizer.fakes.FakeFileRemote
import com.organizer.fakes.FakeFileStore
import com.organizer.fakes.FakeWorkoutDao
import com.organizer.fakes.FakeWorkoutExerciseDao
import com.organizer.fakes.ImmediateSyncTransactionRunner
import com.organizer.fakes.fakeOutboxWriter
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncPayloadFactory
import kotlinx.serialization.json.Json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StarterTemplateRefreshTest {
    private class FakeTemplateDao : StarterTemplateDao {
        val templates = linkedMapOf<Long, StarterTemplateEntity>()
        val exercises = linkedMapOf<Pair<Long, Long>, StarterTemplateExerciseEntity>()
        override fun observeAll() = throw UnsupportedOperationException()
        override fun observeById(id: Long) = throw UnsupportedOperationException()
        override fun observeExercises(templateId: Long) = throw UnsupportedOperationException()
        override suspend fun getByIdOnce(id: Long) = templates[id]
        override suspend fun getExerciseIdsOnce(templateId: Long) =
            exercises.values.filter { it.templateId == templateId }.sortedBy { it.orderIndex }.map { it.exerciseId }
        override suspend fun insertTemplates(items: List<StarterTemplateEntity>) = items.forEach { templates[it.templateId] = it }
        override suspend fun insertExercises(items: List<StarterTemplateExerciseEntity>) =
            items.forEach { exercises[it.templateId to it.exerciseId] = it }
        override suspend fun deleteExercisesForTemplate(templateId: Long) {
            exercises.keys.removeIf { it.first == templateId }
        }
        override suspend fun deleteAllExercises() = exercises.clear()
        override suspend fun deleteAllTemplates() = templates.clear()
        override suspend fun replaceAll(templates: List<StarterTemplateEntity>, exercises: List<StarterTemplateExerciseEntity>) {
            deleteAllExercises(); deleteAllTemplates(); insertTemplates(templates); insertExercises(exercises)
        }
    }

    private fun template(vararg entries: WorkoutTemplateExercise) = WorkoutTemplate(
        workoutId = 1L, name = "Template", exercises = entries.toList(),
    )

    private fun resolved(id: Long, order: Int) = WorkoutTemplateExercise(
        exerciseId = id, orderIndex = order,
        exercise = Exercise(exerciseId = id, name = "E$id", instructions = emptyList(), images = emptyList()),
    )

    private fun unresolved(id: Long, order: Int) = WorkoutTemplateExercise(exerciseId = id, orderIndex = order, exercise = null)

    private class Harness {
        val dao = FakeTemplateDao()
        val exerciseDao = FakeExerciseDao()
        val api = FakeApiService()
        val fileStore = FakeFileStore()
        val mediaQueue = MediaDownloadQueue(fileStore, FakeFileRemote(), TestScope())
        val owner = object : OwnerProvider() { override fun currentOwner() = "user-a" }
        val workouts = FakeWorkoutDao()
        val outbox = com.organizer.fakes.FakeSyncOutboxDao()
        val runner = ImmediateSyncTransactionRunner()
        val workoutRepo = WorkoutRepository(workouts, owner, runner, fakeOutboxWriter(outbox).first)
        val links = FakeWorkoutExerciseDao()
        val associationRepo = WorkoutExerciseRepository(
            links, workouts, exerciseDao, outbox, com.organizer.fakes.FakeSyncStagedDao(), owner, runner,
            fakeOutboxWriter(outbox).first, SyncPayloadFactory(Json { ignoreUnknownKeys = true }), SyncOperationLocks(),
        )
        val repo = StarterTemplateRepository(
            dao, StarterTemplateRemoteDataSource(api), FakeCategoryDao(), FakeExerciseCategoryDao(),
            exerciseDao, workouts, workoutRepo, associationRepo, links,
            FileRepository(fileStore, mediaQueue), owner,
        )
    }

    private suspend fun Harness.seedCatalog(id: Long) {
        exerciseDao.insert(ExerciseEntity(exerciseId = id, name = "Catalog$id", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, ownerFirebaseUid = "public", publicExerciseId = id))
    }

    @Test
    fun `incomplete refresh persists template and resolved associations`() = runTest {
        val h = Harness()
        h.seedCatalog(100)
        h.api.templates = listOf(template(resolved(100, 0), unresolved(999, 1)))
        val unresolved = h.repo.refreshTemplates()
        assertEquals(1, unresolved)
        assertEquals(1, h.dao.templates.size)
        assertEquals(listOf(100L), h.dao.getExerciseIdsOnce(1L))
    }

    @Test
    fun `incomplete refresh preserves existing valid associations`() = runTest {
        val h = Harness()
        h.seedCatalog(100)
        h.seedCatalog(101)
        h.dao.insertExercises(listOf(StarterTemplateExerciseEntity(1L, 101L, 1)))
        h.api.templates = listOf(template(resolved(100, 0), unresolved(999, 1)))
        h.repo.refreshTemplates()
        assertEquals(listOf(100L, 101L), h.dao.getExerciseIdsOnce(1L))
    }

    @Test
    fun `complete refresh replaces associations with correct order and no duplicates`() = runTest {
        val h = Harness()
        h.seedCatalog(100)
        h.seedCatalog(101)
        h.api.templates = listOf(template(resolved(100, 0), unresolved(999, 1)))
        h.repo.refreshTemplates()
        h.api.templates = listOf(template(resolved(101, 0), resolved(100, 1)))
        assertEquals(0, h.repo.refreshTemplates())
        assertEquals(listOf(101L, 100L), h.dao.getExerciseIdsOnce(1L))
        assertEquals(2, h.dao.exercises.size)
        h.repo.refreshTemplates()
        assertEquals(2, h.dao.exercises.size)
    }
}
