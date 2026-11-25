package com.organizer.data.repo

import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.remote.model.ExerciseCategory
import com.organizer.data.remote.repo.ExerciseCategoryRemoteDataSource
import com.organizer.fakes.FakeApiService
import com.organizer.fakes.FakeExerciseCategoryDao
import com.organizer.fakes.FakeExerciseDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExerciseCategoryRepositoryTest {

    private lateinit var api: FakeApiService
    private lateinit var dao: FakeExerciseCategoryDao
    private lateinit var exercises: FakeExerciseDao

    @Before
    fun setUp() {
        api = FakeApiService()
        dao = FakeExerciseCategoryDao()
        exercises = FakeExerciseDao()
    }

    @Test
    fun linksAreInsertedUpdatedAndDeleted() = runTest {
        val repo = ExerciseCategoryRepository(ExerciseCategoryRemoteDataSource(api), dao, exercises)
        exercises.seed(
            ExerciseEntity(exerciseId = 101, name = "Ex 1", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, publicExerciseId = 1),
            ExerciseEntity(exerciseId = 102, name = "Ex 2", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, publicExerciseId = 2),
            ExerciseEntity(exerciseId = 103, name = "Ex 3", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, publicExerciseId = 3),
        )
        dao.seed(
            ExerciseCategoryEntity(1, 101, 10),
            ExerciseCategoryEntity(2, 102, 10),
        )
        api.links = listOf(
            ExerciseCategory(1, 1, 10, 1),
            ExerciseCategory(2, 2, 11, 0),
            ExerciseCategory(3, 3, 10, 0),
        )

        repo.refreshExerciseCategory()

        val all = dao.getAllOnce().associateBy { it.exerciseCategoryId }
        assertEquals(setOf(1L, 2L, 3L), all.keys)
        assertEquals(10L, all.getValue(1).categoryId)
        assertEquals(11L, all.getValue(2).categoryId)
        assertEquals(103L, all.getValue(3).exerciseId)
        assertEquals(listOf(103L, 101L), dao.getExerciseIdsByCategoryId(10))
    }

    @Test
    fun unresolvedCatalogIdDoesNotDeleteExistingLinks() = runTest {
        val repo = ExerciseCategoryRepository(ExerciseCategoryRemoteDataSource(api), dao, exercises)
        exercises.seed(
            ExerciseEntity(exerciseId = 101, name = "Ex 1", instructions = emptyList(), imageUrls = emptyList(), isCustom = false, publicExerciseId = 1),
        )
        dao.seed(ExerciseCategoryEntity(1, 101, 10))
        api.links = listOf(ExerciseCategory(9, 99, 10, 0))

        val unresolved = repo.refreshExerciseCategory()

        assertEquals(1, unresolved)
        assertTrue(dao.getAllOnce().any { it.exerciseId == 101L })
        assertEquals(1, dao.getAllOnce().size)
    }
}
