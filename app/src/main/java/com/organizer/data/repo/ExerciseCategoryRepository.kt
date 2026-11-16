package com.organizer.data.repo

import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.remote.repo.ExerciseCategoryRemoteDataSource
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow

class ExerciseCategoryRepository @Inject constructor(
    private val remoteDataSource: ExerciseCategoryRemoteDataSource,
    private val exerciseCategoryDao: ExerciseCategoryDao,
) {
    suspend fun getExercisesByCategory(categoryId: Long): List<Long> {
        return exerciseCategoryDao.getExerciseIdsByCategoryId(categoryId)
    }

    fun observeCategoriesForExercise(exerciseId: Long): Flow<List<CategoryEntity>> {
        return exerciseCategoryDao.observeCategoriesForExercise(exerciseId)
    }

    fun observeAllLinks(): Flow<List<ExerciseCategoryEntity>> =
        exerciseCategoryDao.getAll()

    suspend fun refreshExerciseCategory() {
        val remote = remoteDataSource.getAll()
        val local = exerciseCategoryDao.getAllOnce()

        val localMap = local.associateBy { it.exerciseCategoryId }
        val remoteIds = remote.map { it.exerciseCategoryId }.toSet()

        val toInsert = remote.filter { it.exerciseCategoryId !in localMap }

        val toUpdate = remote.filter { remoteItem ->
            val localItem = localMap[remoteItem.exerciseCategoryId]
            localItem != null && localItem != remoteItem
        }

        val toDelete = local.filter { it.exerciseCategoryId !in remoteIds }

        toDelete.forEach {
            exerciseCategoryDao.delete(it)
        }

        exerciseCategoryDao.insertAll(toInsert)
        exerciseCategoryDao.updateAll(toUpdate)
    }
}
