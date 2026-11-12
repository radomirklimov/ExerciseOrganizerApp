package com.organizer.data.repo

import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class WorkoutExerciseRepository @Inject constructor(
    private val dao: WorkoutExerciseDao
) {
    suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long) {
        val nextIndex = (dao.getMaxOrderIndex(workoutId) ?: -1) + 1
        dao.insert(WorkoutExerciseEntity(workoutId, exerciseId,nextIndex)
        )
    }

    fun observeExerciseIdsByWorkout(workoutId: Long): Flow<List<Long>> {
        return dao.getExerciseIdsByWorkout(workoutId)
    }

    fun observeWorkoutExerciseById(workoutId: Long, exerciseId: Long): Flow<WorkoutExerciseEntity?> {
        return dao.getWorkoutExercise(workoutId, exerciseId)
    }

    suspend fun updateExerciseOrder(workoutId: Long, exercises: List<ExerciseEntity>) {
        dao.updateExerciseOrder(workoutId, exercises)
    }

    suspend fun deleteExerciseFromWorkout(workoutId: Long, exerciseId: Long) {
        val workoutExercise = observeWorkoutExerciseById(workoutId, exerciseId).firstOrNull()
        if (workoutExercise != null) {
            dao.delete(workoutExercise)
        }
    }
}
