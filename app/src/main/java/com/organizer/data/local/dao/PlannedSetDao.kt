package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.organizer.data.local.db.entities.PlannedSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedSetDao {

    @Query(
        "SELECT * FROM planned_set WHERE workoutId = :workoutId AND exerciseId = :exerciseId " +
            "ORDER BY setNumber ASC"
    )
    fun observePlannedSets(workoutId: Long, exerciseId: Long): Flow<List<PlannedSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<PlannedSetEntity>)

    @Query("DELETE FROM planned_set WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun deleteForExercise(workoutId: Long, exerciseId: Long)

    /**
     * Replaces the whole plan of one exercise. The editor always submits the
     * complete list, so deleting the old rows keeps set numbers contiguous.
     */
    @Transaction
    suspend fun replaceForExercise(
        workoutId: Long,
        exerciseId: Long,
        sets: List<PlannedSetEntity>,
    ) {
        deleteForExercise(workoutId, exerciseId)
        if (sets.isNotEmpty()) {
            insertAll(sets)
        }
    }
}
