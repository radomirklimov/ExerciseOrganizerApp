package com.organizer.data.repo

import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.remote.repo.ExerciseCategoryRemoteDataSource
import jakarta.inject.Inject

class ExerciseCategoryRepository @Inject constructor(
    private val remoteDataSource: ExerciseCategoryRemoteDataSource,
    private val exerciseCategoryDao: ExerciseCategoryDao,
) {
    suspend fun getExercisesByCategory(categoryId: Long): List<Long> {
        return exerciseCategoryDao.getExerciseIdsByCategoryId(categoryId)
    }

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
