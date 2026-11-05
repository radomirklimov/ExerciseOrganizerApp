package com.organizer.data.repo

import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.remote.model.ExerciseCategory
import com.organizer.data.remote.repo.ExerciseCategoryRemoteDataSource
import com.organizer.fakes.FakeApiService
import com.organizer.fakes.FakeExerciseCategoryDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExerciseCategoryRepositoryTest {

    private lateinit var api: FakeApiService
    private lateinit var dao: FakeExerciseCategoryDao

    @Before
    fun setUp() {
        api = FakeApiService()
        dao = FakeExerciseCategoryDao()
    }

    @Test
    fun linksAreInsertedUpdatedAndDeleted() = runTest {
        val repo = ExerciseCategoryRepository(ExerciseCategoryRemoteDataSource(api), dao)
        dao.seed(
            ExerciseCategoryEntity(1, 1, 10),
            ExerciseCategoryEntity(2, 2, 10),
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
        assertEquals(3L, all.getValue(3).exerciseId)
        assertEquals(listOf(3L, 1L), dao.getExerciseIdsByCategoryId(10))
    }
}
