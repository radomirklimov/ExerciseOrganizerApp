package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteExerciseDao {
    @Query("SELECT exerciseId FROM favorite_exercise WHERE ownerFirebaseUid = :owner AND deletedAt IS NULL")
    fun observeFavoriteIds(owner: String): Flow<List<Long>>

    @Query(
        """SELECT e.* FROM exercise e
        INNER JOIN favorite_exercise f ON e.exerciseId = f.exerciseId
        WHERE f.ownerFirebaseUid = :owner AND f.deletedAt IS NULL
        ORDER BY f.addedAt DESC"""
    )
    fun observeFavoriteExercises(owner: String): Flow<List<ExerciseEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_exercise WHERE ownerFirebaseUid = :owner AND exerciseId = :exerciseId AND deletedAt IS NULL)")
    fun observeIsFavorite(owner: String, exerciseId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_exercise WHERE ownerFirebaseUid = :owner AND exerciseId = :exerciseId AND deletedAt IS NULL)")
    suspend fun isFavorite(owner: String, exerciseId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteExerciseEntity)

    @Query("UPDATE favorite_exercise SET deletedAt = :deletedAt, syncStatus = 'PENDING_DELETE' WHERE ownerFirebaseUid = :owner AND exerciseId = :exerciseId")
    suspend fun softDelete(owner: String, exerciseId: Long, deletedAt: Long)
}