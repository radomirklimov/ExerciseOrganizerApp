package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: ExerciseProgressEntity)

    @Query("SELECT * FROM exercise_progress WHERE workoutId = :workoutId AND exerciseId = :exerciseId ORDER BY setNumber ASC, progressId ASC")
    fun observeSets(workoutId: Long, exerciseId: Long): Flow<List<ExerciseProgressEntity>>

    @Query("SELECT COUNT(*) FROM exercise_progress WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun getSetCount(workoutId: Long, exerciseId: Long): Int

    @Query("SELECT * FROM exercise_progress WHERE progressId = :progressId")
    suspend fun getById(progressId: Long): ExerciseProgressEntity?

    @Query("SELECT * FROM exercise_progress WHERE workoutId = :workoutId AND exerciseId = :exerciseId ORDER BY setNumber ASC, progressId ASC")
    suspend fun getSets(workoutId: Long, exerciseId: Long): List<ExerciseProgressEntity>

    @Query("UPDATE exercise_progress SET setNumber = :setNumber WHERE progressId = :progressId")
    suspend fun updateSetNumber(progressId: Long, setNumber: Int)

    @Query("UPDATE exercise_progress SET reps = :reps, weight = :weight, durationSeconds = :durationSeconds WHERE progressId = :progressId")
    suspend fun updateMetrics(progressId: Long, reps: Int?, weight: Double?, durationSeconds: Int?)

    @Query("DELETE FROM exercise_progress WHERE progressId = :progressId")
    suspend fun delete(progressId: Long)

    @Transaction
    suspend fun deleteAndRenumber(progressId: Long) {
        val set = getById(progressId) ?: return
        delete(progressId)
        getSets(set.workoutId, set.exerciseId).forEachIndexed { index, remaining ->
            if (remaining.setNumber != index + 1) {
                updateSetNumber(remaining.progressId, index + 1)
            }
        }
    }
}