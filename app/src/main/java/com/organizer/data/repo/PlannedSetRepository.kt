package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.PlannedSetDao
import com.organizer.data.local.db.entities.PlannedSetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PlannedSetRepository @Inject constructor(
    private val dao: PlannedSetDao,
    private val ownerProvider: OwnerProvider,
) {
    fun observePlannedSets(workoutId: Long, exerciseId: Long): Flow<List<PlannedSetEntity>> {
        return dao.observePlannedSets(ownerProvider.currentOwner(), workoutId, exerciseId)
    }
}