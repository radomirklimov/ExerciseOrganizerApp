package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SuggestedExerciseRow
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutExerciseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workoutExerciseEntity: WorkoutExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(workoutExercises: List<WorkoutExerciseEntity>)

    @Query("SELECT * FROM workout_exercise WHERE ownerFirebaseUid = :owner AND syncId = :syncId")
    suspend fun findBySyncId(owner: String, syncId: String): WorkoutExerciseEntity?

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId AND exerciseId = :exerciseId AND ownerFirebaseUid = :owner")
    suspend fun findByPair(owner: String, workoutId: Long, exerciseId: Long): WorkoutExerciseEntity?

    @Query("SELECT * FROM workout_exercise WHERE ownerFirebaseUid = :owner AND deletedAt IS NOT NULL")
    suspend fun getTombstones(owner: String): List<WorkoutExerciseEntity>

    @Query(
        """UPDATE workout_exercise SET deletedAt = :deletedAt, updatedAt = :updatedAt,
           syncStatus = 'PENDING_DELETE'
           WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId
           AND deletedAt IS NULL"""
    )
    suspend fun softDelete(
        owner: String,
        workoutId: Long,
        exerciseId: Long,
        deletedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """UPDATE workout_exercise SET deletedAt = NULL, updatedAt = :updatedAt,
           syncStatus = 'PENDING_UPLOAD'
           WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId
           AND deletedAt IS NOT NULL"""
    )
    suspend fun restoreLocal(
        owner: String,
        workoutId: Long,
        exerciseId: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """UPDATE workout_exercise SET orderIndex = :orderIndex, updatedAt = :updatedAt,
           syncStatus = 'PENDING_UPLOAD'
           WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId
           AND deletedAt IS NULL"""
    )
    suspend fun updateOrderIndex(owner: String, workoutId: Long, exerciseId: Long, orderIndex: Int, updatedAt: Long)

    @Transaction
    suspend fun updateExerciseOrder(owner: String, workoutId: Long, exercises: List<ExerciseEntity>, updatedAt: Long) {
        exercises.forEachIndexed { index, exercise ->
            updateOrderIndex(owner, workoutId, exercise.exerciseId, index, updatedAt)
        }
    }

    @Query("SELECT exerciseId FROM workout_exercise WHERE workoutId = :workoutId AND ownerFirebaseUid = :owner AND deletedAt IS NULL ORDER BY orderIndex ASC")
    fun getExerciseIdsByWorkout(owner: String, workoutId: Long): Flow<List<Long>>

    @Query("SELECT * FROM workout_exercise WHERE ownerFirebaseUid = :owner AND deletedAt IS NULL")
    fun getAll(owner: String): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT MAX(orderIndex) FROM workout_exercise WHERE workoutId = :workoutId AND ownerFirebaseUid = :owner AND deletedAt IS NULL")
    suspend fun getMaxOrderIndex(owner: String, workoutId: Long): Int?

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId AND exerciseId = :exerciseId AND ownerFirebaseUid = :owner AND deletedAt IS NULL")
    fun getWorkoutExercise(owner: String, workoutId: Long, exerciseId: Long): Flow<WorkoutExerciseEntity?>

    @Query(
        """SELECT e.*, we.workoutId AS sourceWorkoutId FROM exercise e
        INNER JOIN workout_exercise we ON e.exerciseId = we.exerciseId
        WHERE we.ownerFirebaseUid = :owner AND we.deletedAt IS NULL AND e.deletedAt IS NULL"""
    )
    fun observeSuggestedExerciseRows(owner: String): Flow<List<SuggestedExerciseRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRemote(link: WorkoutExerciseEntity)

    /** Updates only mutable fields; never rewrites the local stable syncId. */
    @Query(
        """UPDATE workout_exercise SET orderIndex = :orderIndex, updatedAt = :updatedAt,
           deletedAt = NULL, syncStatus = 'SYNCED', serverVersion = :serverVersion
           WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId
           AND serverVersion < :serverVersion"""
    )
    suspend fun applyRemoteUpsert(
        owner: String,
        workoutId: Long,
        exerciseId: Long,
        orderIndex: Int,
        serverVersion: Long,
        updatedAt: Long,
    )

    @Query(
        """UPDATE workout_exercise SET deletedAt = :deletedAt, updatedAt = :updatedAt,
           syncStatus = 'SYNCED', serverVersion = :serverVersion
           WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId
           AND serverVersion < :serverVersion"""
    )
    suspend fun applyRemoteDelete(
        owner: String,
        workoutId: Long,
        exerciseId: Long,
        deletedAt: Long,
        serverVersion: Long,
        updatedAt: Long,
    )

    @Query(
        """UPDATE workout_exercise SET deletedAt = :deletedAt, updatedAt = :updatedAt,
           syncStatus = 'SYNCED', serverVersion = :serverVersion
           WHERE ownerFirebaseUid = :owner AND syncId = :syncId
           AND serverVersion < :serverVersion"""
    )
    suspend fun applyRemoteDeleteBySyncId(
        owner: String,
        syncId: String,
        deletedAt: Long,
        serverVersion: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """UPDATE workout_exercise SET syncStatus = :syncStatus,
           updatedAt = max(updatedAt, :serverUpdatedAt), serverVersion = :serverVersion
           WHERE ownerFirebaseUid = :owner AND syncId = :syncId
           AND serverVersion < :serverVersion"""
    )
    suspend fun applyAcknowledgementMetadata(
        owner: String,
        syncId: String,
        serverVersion: Long,
        serverUpdatedAt: Long,
        syncStatus: String,
    )
}
