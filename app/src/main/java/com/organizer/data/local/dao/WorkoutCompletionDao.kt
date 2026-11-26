package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.WorkoutCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutCompletionDao {
    @Query("SELECT * FROM workout_completion WHERE ownerFirebaseUid = :owner AND deletedAt IS NULL")
    fun observeAll(owner: String): Flow<List<WorkoutCompletionEntity>>

    @Query("SELECT * FROM workout_completion WHERE ownerFirebaseUid = :owner AND syncId = :syncId")
    suspend fun findBySyncId(owner: String, syncId: String): WorkoutCompletionEntity?

    @Query("UPDATE workout_completion SET syncId = :newSyncId, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND dateKey = :dateKey AND workoutId = :workoutId AND syncId = :oldSyncId")
    suspend fun updateSyncId(owner: String, dateKey: Int, workoutId: Long, oldSyncId: String, newSyncId: String, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(completion: WorkoutCompletionEntity)

    @Query("UPDATE workout_completion SET syncStatus = 'SYNCED', serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyAcknowledgementMetadata(owner: String, syncId: String, serverVersion: Long, updatedAt: Long)

    @Query("UPDATE workout_completion SET completedExercises = :completedExercises, totalExercises = :totalExercises, completedAt = :completedAt, syncStatus = 'SYNCED', serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyRemoteUpsert(owner: String, syncId: String, completedExercises: Int, totalExercises: Int, completedAt: Long, serverVersion: Long, updatedAt: Long): Int
}