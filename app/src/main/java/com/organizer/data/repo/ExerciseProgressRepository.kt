package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ExerciseProgressDao
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExerciseProgressRepository @Inject constructor(
    private val dao: ExerciseProgressDao,
    private val ownerProvider: OwnerProvider,
) {
    fun observeSets(workoutId: Long, exerciseId: Long): Flow<List<ExerciseProgressEntity>> {
        return dao.observeSets(ownerProvider.currentOwner(), workoutId, exerciseId)
    }

    fun observeSessionSets(workoutId: Long, exerciseId: Long, sessionId: Long): Flow<List<ExerciseProgressEntity>> {
        return dao.observeSessionSets(ownerProvider.currentOwner(), workoutId, exerciseId, sessionId)
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
                ownerFirebaseUid = ownerProvider.currentOwner(),
            )
        )
    }

    suspend fun updateSet(
        progressId: Long,
        reps: Int? = null,
        weight: Double? = null,
        durationSeconds: Int? = null,
    ) {
        dao.updateMetrics(ownerProvider.currentOwner(), progressId, reps, weight, durationSeconds, System.currentTimeMillis())
    }

    suspend fun deleteSet(progressId: Long) {
        dao.deleteAndRenumber(ownerProvider.currentOwner(), progressId, System.currentTimeMillis(), System.currentTimeMillis())
    }
}