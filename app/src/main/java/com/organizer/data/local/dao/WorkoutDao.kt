package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workoutEntity: WorkoutEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM workout WHERE name = :name)")
    suspend fun nameExists(name: String): Boolean

    @Query("SELECT * FROM workout ORDER BY workoutId DESC")
    fun getAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout WHERE workoutId = :id")
    fun getById(id: Long): Flow<WorkoutEntity?>

    @Query("DELETE FROM workout WHERE workoutId = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE workout SET notes = :notes WHERE workoutId = :id")
    suspend fun updateNotes(id: Long, notes: String)
}
