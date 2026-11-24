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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(completion: WorkoutCompletionEntity)
}