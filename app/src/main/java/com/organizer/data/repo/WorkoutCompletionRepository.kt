package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.WorkoutCompletionDao
import com.organizer.data.local.db.entities.WorkoutCompletionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WorkoutCompletionRepository @Inject constructor(
    private val dao: WorkoutCompletionDao,
    private val ownerProvider: OwnerProvider,
) {
    fun observeCompletions(): Flow<List<WorkoutCompletionEntity>> = dao.observeAll(ownerProvider.currentOwner())

    fun observeCompletionPercentByDate(): Flow<Map<Int, Int>> =
        dao.observeAll(ownerProvider.currentOwner()).map { completions ->
            completions
                .groupBy { it.dateKey }
                .mapValues { (_, day) ->
                    val total = day.sumOf { it.totalExercises }
                    if (total <= 0) {
                        0
                    } else {
                        (day.sumOf { it.completedExercises } * 100 + total / 2) / total
                    }
                }
        }

    suspend fun recordCompletion(
        dateKey: Int,
        workoutId: Long,
        completedExercises: Int,
        totalExercises: Int,
    ) {
        if (totalExercises <= 0) return
        dao.upsert(
            WorkoutCompletionEntity(
                dateKey = dateKey,
                workoutId = workoutId,
                completedExercises = completedExercises.coerceIn(0, totalExercises),
                totalExercises = totalExercises,
                ownerFirebaseUid = ownerProvider.currentOwner(),
            )
        )
    }
}