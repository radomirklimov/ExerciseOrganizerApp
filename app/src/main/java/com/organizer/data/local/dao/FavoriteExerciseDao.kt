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

    @Query("SELECT exerciseId FROM favorite_exercise")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Query(
        """SELECT e.* FROM exercise e
        JOIN favorite_exercise f ON e.exerciseId = f.exerciseId
        ORDER BY f.addedAt DESC, e.exerciseId DESC"""
    )
    fun observeFavoriteExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_exercise WHERE exerciseId = :exerciseId)")
    fun observeIsFavorite(exerciseId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_exercise WHERE exerciseId = :exerciseId)")
    suspend fun isFavorite(exerciseId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteExerciseEntity)

    @Query("DELETE FROM favorite_exercise WHERE exerciseId = :exerciseId")
    suspend fun deleteById(exerciseId: Long)
}
