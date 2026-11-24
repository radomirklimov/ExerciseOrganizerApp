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

    @Query("SELECT EXISTS(SELECT 1 FROM workout WHERE name = :name AND ownerFirebaseUid = :owner AND deletedAt IS NULL)")
    suspend fun nameExists(name: String, owner: String): Boolean

    @Query("SELECT * FROM workout WHERE ownerFirebaseUid = :owner AND deletedAt IS NULL ORDER BY workoutId DESC")
    fun getAll(owner: String): Flow<List<WorkoutEntity>>

    @Query(
        """SELECT w.workoutId AS workoutId,
               w.name AS name,
               COUNT(we.exerciseId) AS exerciseCount
        FROM workout w
        LEFT JOIN workout_exercise we ON we.workoutId = w.workoutId
        WHERE w.ownerFirebaseUid = :owner AND w.deletedAt IS NULL
        GROUP BY w.workoutId, w.name
        ORDER BY w.workoutId DESC"""
    )
    fun getWorkoutsWithExerciseCount(owner: String): Flow<List<WorkoutWithExerciseCount>>

    @Query("SELECT * FROM workout WHERE workoutId = :id AND ownerFirebaseUid = :owner AND deletedAt IS NULL")
    fun getById(owner: String, id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout WHERE workoutId = :id AND ownerFirebaseUid = :owner AND deletedAt IS NULL")
    suspend fun getByIdOnce(owner: String, id: Long): WorkoutEntity?

    @Query("UPDATE workout SET deletedAt = :deletedAt, syncStatus = 'PENDING_DELETE' WHERE workoutId = :id AND ownerFirebaseUid = :owner")
    suspend fun softDelete(owner: String, id: Long, deletedAt: Long)

    @Query("UPDATE workout SET notes = :notes, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE workoutId = :id AND ownerFirebaseUid = :owner")
    suspend fun updateNotes(owner: String, id: Long, notes: String, updatedAt: Long)
}