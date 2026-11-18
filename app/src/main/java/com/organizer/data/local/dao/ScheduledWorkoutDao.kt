package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledWorkoutDao {

    @Query(
        "SELECT * FROM scheduled_workout " +
            "ORDER BY dayOfWeek, startMinutes, scheduleId"
    )
    fun observeAll(): Flow<List<ScheduledWorkoutEntity>>

    @Query("SELECT * FROM scheduled_workout")
    suspend fun getAllOnce(): List<ScheduledWorkoutEntity>

    @Query("SELECT * FROM scheduled_workout WHERE scheduleId = :id")
    suspend fun getById(id: Long): ScheduledWorkoutEntity?

    @Query("SELECT * FROM scheduled_workout WHERE workoutId = :workoutId")
    suspend fun getForWorkout(workoutId: Long): List<ScheduledWorkoutEntity>

    @Insert
    suspend fun insert(entry: ScheduledWorkoutEntity): Long

    @Update
    suspend fun update(entry: ScheduledWorkoutEntity)

    @Query("UPDATE scheduled_workout SET reminderNote = :note WHERE scheduleId = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("DELETE FROM scheduled_workout WHERE scheduleId = :id")
    suspend fun delete(id: Long)
}
