package com.organizer.data.repo

import com.organizer.data.local.dao.ExerciseProgressDao
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExerciseProgressRepository @Inject constructor(
    private val dao: ExerciseProgressDao,
) {
    /** Every set ever recorded for the exercise, across sessions. */
    fun observeSets(workoutId: Long, exerciseId: Long): Flow<List<ExerciseProgressEntity>> {
        return dao.observeSets(workoutId, exerciseId)
    }

    /** Only the sets logged while the given session is running. */
    fun observeSessionSets(
        workoutId: Long,
        exerciseId: Long,
        sessionId: Long,
    ): Flow<List<ExerciseProgressEntity>> {
        return dao.observeSessionSets(workoutId, exerciseId, sessionId)
    }

    suspend fun addSet(
        workoutId: Long,
        exerciseId: Long,
        setNumber: Int,
        reps: Int? = null,
        weight: Double? = null,
        durationSeconds: Int? = null,
        sessionId: Long = 0L,
        unitSystem: String = "METRIC",
    ) {
        dao.insert(
            ExerciseProgressEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                reps = reps,
                weight = weight,
                durationSeconds = durationSeconds,
                sessionId = sessionId,
                unitSystem = unitSystem,
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
