package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.organizer.data.local.db.entities.PlannedSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedSetDao {

    @Query(
        "SELECT * FROM planned_set WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId AND deletedAt IS NULL " +
            "ORDER BY setNumber ASC"
    )
    fun observePlannedSets(owner: String, workoutId: Long, exerciseId: Long): Flow<List<PlannedSetEntity>>
}