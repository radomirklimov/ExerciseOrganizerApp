package com.organizer.data.repo

import com.organizer.data.local.dao.ExerciseProgressDao
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExerciseProgressRepository @Inject constructor(
    private val dao: ExerciseProgressDao,
) {
    fun observeSets(workoutId: Long, exerciseId: Long): Flow<List<ExerciseProgressEntity>> {
        return dao.observeSets(workoutId, exerciseId)
    }

    suspend fun addSet(
        workoutId: Long,
        exerciseId: Long,
        setNumber: Int,
        reps: Int? = null,
        weight: Double? = null,
        durationSeconds: Int? = null,
    ) {
        dao.insert(
            ExerciseProgressEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                reps = reps,
                weight = weight,
                durationSeconds = durationSeconds,
            )
        )
    }

    suspend fun updateSet(
        progressId: Long,
        reps: Int? = null,
        weight: Double? = null,
        durationSeconds: Int? = null,
    ) {
        dao.updateMetrics(progressId, reps, weight, durationSeconds)
    }

    suspend fun deleteSet(progressId: Long) {
        dao.deleteAndRenumber(progressId)
    }
}