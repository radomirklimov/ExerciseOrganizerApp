package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SuggestedExerciseRow
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.random.Random

class WorkoutExerciseRepository @Inject constructor(
    private val dao: WorkoutExerciseDao,
    private val ownerProvider: OwnerProvider,
) {
    suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long) {
        val owner = ownerProvider.currentOwner()
        val nextIndex = (dao.getMaxOrderIndex(owner, workoutId) ?: -1) + 1
        dao.insert(
            WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                orderIndex = nextIndex,
                ownerFirebaseUid = owner,
            )
        )
    }

    fun observeExerciseIdsByWorkout(workoutId: Long): Flow<List<Long>> {
        return dao.getExerciseIdsByWorkout(ownerProvider.currentOwner(), workoutId)
    }

    fun observeAllWorkoutExercises(): Flow<List<WorkoutExerciseEntity>> {
        return dao.getAll(ownerProvider.currentOwner())
    }

    fun observeSuggestedExercises(limit: Int): Flow<List<ExerciseEntity>> {
        return dao.observeSuggestedExerciseRows(ownerProvider.currentOwner()).map { rows ->
            mixSuggestedExercises(rows, limit)
        }
    }

    fun observeWorkoutExerciseById(workoutId: Long, exerciseId: Long): Flow<WorkoutExerciseEntity?> {
        return dao.getWorkoutExercise(ownerProvider.currentOwner(), workoutId, exerciseId)
    }

    suspend fun updateExerciseOrder(workoutId: Long, exercises: List<ExerciseEntity>) {
        dao.updateExerciseOrder(ownerProvider.currentOwner(), workoutId, exercises, System.currentTimeMillis())
    }

    suspend fun deleteExerciseFromWorkout(workoutId: Long, exerciseId: Long) {
        val owner = ownerProvider.currentOwner()
        val link = observeWorkoutExerciseById(workoutId, exerciseId).firstOrNull()
        if (link != null && link.ownerFirebaseUid == owner) {
            dao.delete(link.copy(deletedAt = System.currentTimeMillis(), syncStatus = "PENDING_DELETE"))
        }
    }
}

internal fun mixSuggestedExercises(
    rows: List<SuggestedExerciseRow>,
    limit: Int,
    random: Random = Random.Default,
): List<ExerciseEntity> {
    if (limit <= 0) return emptyList()

    val workoutBuckets = rows
        .groupBy { it.sourceWorkoutId }
        .entries
        .sortedByDescending { it.key }
        .map { (_, workoutRows) ->
            workoutRows.map { it.exercise }.shuffled(random).toMutableList()
        }

    val suggestions = mutableListOf<ExerciseEntity>()
    val seenExerciseIds = mutableSetOf<Long>()

    var addedInPass = true
    while (suggestions.size < limit && addedInPass) {
        addedInPass = false

        for (bucket in workoutBuckets) {
            while (bucket.isNotEmpty()) {
                val exercise = bucket.removeAt(0)
                if (seenExerciseIds.add(exercise.exerciseId)) {
                    suggestions += exercise
                    addedInPass = true
                    break
                }
            }

            if (suggestions.size == limit) break
        }
    }

    return suggestions
}