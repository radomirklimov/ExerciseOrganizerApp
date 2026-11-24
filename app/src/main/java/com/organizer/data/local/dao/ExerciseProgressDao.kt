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

    @Query("SELECT * FROM exercise_progress WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId AND deletedAt IS NULL ORDER BY setNumber ASC, progressId ASC")
    fun observeSets(owner: String, workoutId: Long, exerciseId: Long): Flow<List<ExerciseProgressEntity>>

    @Query(
        "SELECT * FROM exercise_progress WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId " +
            "AND exerciseId = :exerciseId AND sessionId = :sessionId AND deletedAt IS NULL " +
            "ORDER BY setNumber ASC, progressId ASC"
    )
    fun observeSessionSets(
        owner: String,
        workoutId: Long,
        exerciseId: Long,
        sessionId: Long,
    ): Flow<List<ExerciseProgressEntity>>

    @Query(
        "SELECT * FROM exercise_progress WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId " +
            "AND exerciseId = :exerciseId AND sessionId = :sessionId AND deletedAt IS NULL " +
            "ORDER BY setNumber ASC, progressId ASC"
    )
    suspend fun getSessionSets(
        owner: String,
        workoutId: Long,
        exerciseId: Long,
        sessionId: Long,
    ): List<ExerciseProgressEntity>

    @Query("SELECT COUNT(*) FROM exercise_progress WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId AND deletedAt IS NULL")
    suspend fun getSetCount(owner: String, workoutId: Long, exerciseId: Long): Int

    @Query("SELECT * FROM exercise_progress WHERE ownerFirebaseUid = :owner AND progressId = :progressId AND deletedAt IS NULL")
    suspend fun getById(owner: String, progressId: Long): ExerciseProgressEntity?

    @Query("SELECT * FROM exercise_progress WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId AND deletedAt IS NULL ORDER BY setNumber ASC, progressId ASC")
    suspend fun getSets(owner: String, workoutId: Long, exerciseId: Long): List<ExerciseProgressEntity>

    @Query("UPDATE exercise_progress SET setNumber = :setNumber, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE ownerFirebaseUid = :owner AND progressId = :progressId")
    suspend fun updateSetNumber(owner: String, progressId: Long, setNumber: Int, updatedAt: Long)

    @Query("UPDATE exercise_progress SET reps = :reps, weight = :weight, durationSeconds = :durationSeconds, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE ownerFirebaseUid = :owner AND progressId = :progressId")
    suspend fun updateMetrics(owner: String, progressId: Long, reps: Int?, weight: Double?, durationSeconds: Int?, updatedAt: Long)

    @Query("UPDATE exercise_progress SET deletedAt = :deletedAt, syncStatus = 'PENDING_DELETE' WHERE ownerFirebaseUid = :owner AND progressId = :progressId")
    suspend fun softDelete(owner: String, progressId: Long, deletedAt: Long)

    @Transaction
    suspend fun deleteAndRenumber(owner: String, progressId: Long, deletedAt: Long, updatedAt: Long) {
        val set = getById(owner, progressId) ?: return
        softDelete(owner, progressId, deletedAt)
        getSessionSets(owner, set.workoutId, set.exerciseId, set.sessionId)
            .forEachIndexed { index, remaining ->
                if (remaining.setNumber != index + 1) {
                    updateSetNumber(owner, remaining.progressId, index + 1, updatedAt)
                }
            }
    }
}