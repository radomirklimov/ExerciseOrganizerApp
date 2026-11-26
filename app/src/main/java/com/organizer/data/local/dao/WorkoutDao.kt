package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workoutEntity: WorkoutEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM workout WHERE normalizedName = :normalizedName AND ownerFirebaseUid = :owner AND deletedAt IS NULL)")
    suspend fun nameExists(normalizedName: String, owner: String): Boolean

    @Query("SELECT * FROM workout WHERE ownerFirebaseUid = :owner AND deletedAt IS NULL ORDER BY workoutId DESC")
    fun getAll(owner: String): Flow<List<WorkoutEntity>>

    @Query(
        """SELECT w.workoutId AS workoutId,
               w.name AS name,
               COUNT(we.exerciseId) AS exerciseCount
        FROM workout w
        LEFT JOIN workout_exercise we ON we.workoutId = w.workoutId AND we.deletedAt IS NULL
        WHERE w.ownerFirebaseUid = :owner AND w.deletedAt IS NULL
        GROUP BY w.workoutId, w.name
        ORDER BY w.workoutId DESC"""
    )
    fun getWorkoutsWithExerciseCount(owner: String): Flow<List<WorkoutWithExerciseCount>>

    @Query("SELECT * FROM workout WHERE workoutId = :id AND ownerFirebaseUid = :owner AND deletedAt IS NULL")
    fun getById(owner: String, id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout WHERE workoutId = :id AND ownerFirebaseUid = :owner AND deletedAt IS NULL")
    suspend fun getByIdOnce(owner: String, id: Long): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE syncId = :syncId AND ownerFirebaseUid = :owner")
    suspend fun findBySyncId(owner: String, syncId: String): WorkoutEntity?

    @Query(
        """UPDATE workout
           SET name = :name, notes = :notes, normalizedName = :normalizedName,
               serverVersion = :serverVersion, updatedAt = :updatedAt, deletedAt = NULL,
               syncStatus = 'SYNCED'
           WHERE syncId = :syncId AND ownerFirebaseUid = :owner"""
    )
    suspend fun applyRemoteUpsert(
        owner: String,
        syncId: String,
        name: String,
        notes: String,
        normalizedName: String,
        serverVersion: Long,
        updatedAt: Long,
    )

    @Query(
        """UPDATE workout
           SET deletedAt = :deletedAt, normalizedName = NULL, serverVersion = :serverVersion,
               updatedAt = :updatedAt, syncStatus = 'SYNCED'
           WHERE syncId = :syncId AND ownerFirebaseUid = :owner"""
    )
    suspend fun applyRemoteDelete(
        owner: String,
        syncId: String,
        deletedAt: Long,
        serverVersion: Long,
        updatedAt: Long,
    )

    @Query(
        """UPDATE workout
           SET serverVersion = :serverVersion,
               updatedAt = MAX(updatedAt, :serverUpdatedAt),
               syncStatus = :syncStatus
           WHERE ownerFirebaseUid = :owner
           AND syncId = :syncId
           AND serverVersion < :serverVersion"""
    )
    suspend fun applyAcknowledgementMetadata(
        owner: String,
        syncId: String,
        serverVersion: Long,
        serverUpdatedAt: Long,
        syncStatus: String,
    )

    @Query("UPDATE workout SET deletedAt = :deletedAt, normalizedName = NULL, syncStatus = 'PENDING_DELETE' WHERE workoutId = :id AND ownerFirebaseUid = :owner")
    suspend fun softDelete(owner: String, id: Long, deletedAt: Long)

    @Query("UPDATE workout SET notes = :notes, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE workoutId = :id AND ownerFirebaseUid = :owner AND deletedAt IS NULL")
    suspend fun updateNotes(owner: String, id: Long, notes: String, updatedAt: Long)
}