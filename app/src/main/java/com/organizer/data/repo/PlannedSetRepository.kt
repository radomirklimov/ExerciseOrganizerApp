package com.organizer.data.repo

import com.organizer.data.local.dao.PlannedSetDao
import com.organizer.data.local.db.entities.PlannedSetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PlannedSetRepository @Inject constructor(
    private val dao: PlannedSetDao,
) {
    fun observePlannedSets(workoutId: Long, exerciseId: Long): Flow<List<PlannedSetEntity>> {
        return dao.observePlannedSets(workoutId, exerciseId)
    }

    suspend fun replacePlannedSets(
        workoutId: Long,
        exerciseId: Long,
        sets: List<PlannedSetEntity>,
    ) {
        dao.replaceForExercise(workoutId, exerciseId, sets)
    }
}
