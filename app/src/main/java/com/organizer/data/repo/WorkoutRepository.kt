package com.organizer.data.repo

import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao
) {
    suspend fun createWorkout(name: String, notes: String = "") {
        dao.insert(WorkoutEntity(name = name, notes = notes))
    }

    fun observeWorkouts(): Flow<List<WorkoutEntity>> {
        return dao.getAll()
    }

    fun observeWorkoutById(id: Long): Flow<WorkoutEntity?> {
        return dao.getById(id)
    }

    suspend fun deleteWorkout(id: Long) {
        dao.delete(id)
    }

    suspend fun updateWorkoutNotes(id: Long, notes: String) {
        dao.updateNotes(id, notes)
    }
}
