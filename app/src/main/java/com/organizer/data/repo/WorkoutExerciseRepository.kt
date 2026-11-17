package com.organizer.data.repo

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

    fun observeAllWorkoutExercises(): Flow<List<WorkoutExerciseEntity>> {
        return dao.getAll()
    }

    fun observeSuggestedExercises(limit: Int): Flow<List<ExerciseEntity>> {
        return dao.observeSuggestedExerciseRows().map { rows ->
            mixSuggestedExercises(rows, limit)
        }
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

/**
 * Builds a mixed suggestion list from the exercises of all workouts.
 *
 * Workouts are visited newest first (higher [SuggestedExerciseRow.sourceWorkoutId]
 * means it was created later) and exercises are taken round-robin, one workout
 * per pass, in a random order inside each workout. That keeps the list from
 * reading like a single workout while the most recently created workouts still
 * lead. An exercise that appears in several workouts is only suggested once.
 */
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
