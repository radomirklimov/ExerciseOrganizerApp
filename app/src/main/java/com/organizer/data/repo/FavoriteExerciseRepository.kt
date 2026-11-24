package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FavoriteExerciseRepository @Inject constructor(
    private val favoriteExerciseDao: FavoriteExerciseDao,
    private val ownerProvider: OwnerProvider,
) {
    fun observeFavoriteExerciseIds(): Flow<List<Long>> =
        favoriteExerciseDao.observeFavoriteIds(ownerProvider.currentOwner())

    fun observeFavoriteExercises(): Flow<List<ExerciseEntity>> =
        favoriteExerciseDao.observeFavoriteExercises(ownerProvider.currentOwner())

    fun observeIsFavorite(exerciseId: Long): Flow<Boolean> =
        favoriteExerciseDao.observeIsFavorite(ownerProvider.currentOwner(), exerciseId)

    suspend fun toggleFavorite(exerciseId: Long) {
        val owner = ownerProvider.currentOwner()
        if (favoriteExerciseDao.isFavorite(owner, exerciseId)) {
            favoriteExerciseDao.softDelete(owner, exerciseId, System.currentTimeMillis())
        } else {
            favoriteExerciseDao.insert(FavoriteExerciseEntity(exerciseId = exerciseId, ownerFirebaseUid = owner))
        }
    }

    suspend fun addFavorite(exerciseId: Long) {
        favoriteExerciseDao.insert(
            FavoriteExerciseEntity(
                exerciseId = exerciseId,
                ownerFirebaseUid = ownerProvider.currentOwner(),
            )
        )
    }

    suspend fun removeFavorite(exerciseId: Long) {
        favoriteExerciseDao.softDelete(ownerProvider.currentOwner(), exerciseId, System.currentTimeMillis())
    }
}