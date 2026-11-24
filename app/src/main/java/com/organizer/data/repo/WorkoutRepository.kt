package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
    private val ownerProvider: OwnerProvider,
) {
    suspend fun createWorkout(name: String, notes: String = ""): Long {
        return dao.insert(WorkoutEntity(name = name, notes = notes, ownerFirebaseUid = ownerProvider.currentOwner()))
    }

    fun observeWorkouts(): Flow<List<WorkoutEntity>> {
        return dao.getAll(ownerProvider.currentOwner())
    }

    fun observeWorkoutsWithExerciseCount(): Flow<List<WorkoutWithExerciseCount>> {
        return dao.getWorkoutsWithExerciseCount(ownerProvider.currentOwner())
    }

    fun observeWorkoutById(id: Long): Flow<WorkoutEntity?> {
        return dao.getById(ownerProvider.currentOwner(), id)
    }

    suspend fun deleteWorkout(id: Long) {
        dao.softDelete(ownerProvider.currentOwner(), id, System.currentTimeMillis())
    }

    suspend fun updateWorkoutNotes(id: Long, notes: String) {
        dao.updateNotes(ownerProvider.currentOwner(), id, notes, System.currentTimeMillis())
    }
}