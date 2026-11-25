package com.organizer.data.repo

import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.remote.repo.ExerciseCategoryRemoteDataSource
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow

class ExerciseCategoryRepository @Inject constructor(
    private val remoteDataSource: ExerciseCategoryRemoteDataSource,
    private val exerciseCategoryDao: ExerciseCategoryDao,
    private val exerciseDao: ExerciseDao,
) {
    suspend fun getExercisesByCategory(categoryId: Long): List<Long> {
        return exerciseCategoryDao.getExerciseIdsByCategoryId(categoryId)
    }

    fun observeCategoriesForExercise(exerciseId: Long): Flow<List<CategoryEntity>> {
        return exerciseCategoryDao.observeCategoriesForExercise(exerciseId)
    }

    fun observeAllLinks(): Flow<List<ExerciseCategoryEntity>> =
        exerciseCategoryDao.getAll()

    /** Returns the number of catalog links skipped because their exercise was unresolved. */
    suspend fun refreshExerciseCategory(): Int {
        // Catalog exercise IDs are not Room PKs; resolve them through public rows.
        val catalogToLocal = exerciseDao.getAllPublicOnce()
            .mapNotNull { row -> row.publicExerciseId?.let { it to row.exerciseId } }
            .toMap()
        var unresolved = 0
        val incoming = remoteDataSource.getAll()
        val remote = incoming.mapNotNull { link ->
            catalogToLocal[link.exerciseId]?.let { link.copy(exerciseId = it) }
                ?: run { unresolved++; null }
        }
        val local = exerciseCategoryDao.getAllOnce()
        val localMap = local.associateBy { it.exerciseCategoryId }

        val toInsert = remote.filter { it.exerciseCategoryId !in localMap }
        val toUpdate = remote.filter { remoteItem ->
            val localItem = localMap[remoteItem.exerciseCategoryId]
            localItem != null && localItem != remoteItem
        }

        // Incomplete refreshes must not remove existing links: a catalog ID
        // that could not be resolved says nothing about whether the link is
        // still wanted. Deletion is intentionally deferred to a full refresh.
        exerciseCategoryDao.insertAll(toInsert)
        exerciseCategoryDao.updateAll(toUpdate)
        return unresolved
    }
}
