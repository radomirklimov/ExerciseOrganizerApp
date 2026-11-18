package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.organizer.data.local.db.entities.PlannedSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedSetDao {

    @Query(
        "SELECT * FROM planned_set WHERE workoutId = :workoutId AND exerciseId = :exerciseId " +
            "ORDER BY setNumber ASC"
    )
    fun observePlannedSets(workoutId: Long, exerciseId: Long): Flow<List<PlannedSetEntity>>
}
