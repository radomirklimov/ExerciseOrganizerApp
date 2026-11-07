package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: ExerciseProgressEntity)

    @Query("SELECT * FROM exercise_progress WHERE workoutId = :workoutId AND exerciseId = :exerciseId ORDER BY setNumber ASC, progressId ASC")
    fun observeSets(workoutId: Long, exerciseId: Long): Flow<List<ExerciseProgressEntity>>

    @Query("SELECT * FROM exercise_progress WHERE exerciseId = :exerciseId ORDER BY recordedAt DESC LIMIT :limit")
    fun observeHistory(exerciseId: Long, limit: Int): Flow<List<ExerciseProgressEntity>>

    @Query("SELECT COUNT(*) FROM exercise_progress WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun getSetCount(workoutId: Long, exerciseId: Long): Int

    @Query("DELETE FROM exercise_progress WHERE progressId = :progressId")
    suspend fun delete(progressId: Long)
}