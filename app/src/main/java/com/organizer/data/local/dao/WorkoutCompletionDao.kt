package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.WorkoutCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutCompletionDao {

    @Query("SELECT * FROM workout_completion")
    fun observeAll(): Flow<List<WorkoutCompletionEntity>>

    /** Repeating the same workout on the same day replaces that day's result. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(completion: WorkoutCompletionEntity)
}
