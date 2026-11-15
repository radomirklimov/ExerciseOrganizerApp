package com.organizer.data.repo

import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Stores the exercises the user liked. Favorites are user data, so they live
 * outside the synced exercise rows and survive library refreshes.
 */
class FavoriteExerciseRepository @Inject constructor(
    private val favoriteExerciseDao: FavoriteExerciseDao,
) {
    fun observeFavoriteExerciseIds(): Flow<List<Long>> =
        favoriteExerciseDao.observeFavoriteIds()

    fun observeFavoriteExercises(): Flow<List<ExerciseEntity>> =
        favoriteExerciseDao.observeFavoriteExercises()

    fun observeIsFavorite(exerciseId: Long): Flow<Boolean> =
        favoriteExerciseDao.observeIsFavorite(exerciseId)

    /** Likes an unliked exercise and unlikes a liked one. */
    suspend fun toggleFavorite(exerciseId: Long) {
        if (favoriteExerciseDao.isFavorite(exerciseId)) {
            favoriteExerciseDao.deleteById(exerciseId)
        } else {
            favoriteExerciseDao.insert(FavoriteExerciseEntity(exerciseId = exerciseId))
        }
    }

    suspend fun addFavorite(exerciseId: Long) {
        favoriteExerciseDao.insert(FavoriteExerciseEntity(exerciseId = exerciseId))
    }

    suspend fun removeFavorite(exerciseId: Long) {
        favoriteExerciseDao.deleteById(exerciseId)
    }
}
