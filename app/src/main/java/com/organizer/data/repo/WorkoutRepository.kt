package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
    private val ownerProvider: OwnerProvider,
    private val transactions: SyncTransactionRunner,
    private val outboxWriter: SyncOutboxWriter,
) {
    suspend fun createWorkout(name: String, notes: String = ""): Long = createWorkoutInternal(name, notes)

    /** Used when materialising a starter template into the user's own workouts. */
    suspend fun createImportedWorkout(name: String, notes: String = ""): Long = createWorkoutInternal(name, notes)

    private suspend fun createWorkoutInternal(name: String, notes: String): Long {
        val owner = ownerProvider.currentOwner()
        val entity = WorkoutEntity(
            name = name,
            notes = notes,
            ownerFirebaseUid = owner,
            normalizedName = WorkoutNameNormalizer.normalize(name),
        )
        return transactions.run {
            val id = dao.insert(entity)
            outboxWriter.upsertWorkout(owner, entity.copy(workoutId = id))
            id
        }
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
        val owner = ownerProvider.currentOwner()
        val now = System.currentTimeMillis()
        transactions.run {
            val workout = dao.getByIdOnce(owner, id)
            if (workout != null && workout.deletedAt == null) {
                dao.softDelete(owner, id, now)
                outboxWriter.deleteWorkout(owner, workout.copy(deletedAt = now))
            }
        }
    }

    suspend fun updateWorkoutNotes(id: Long, notes: String) {
        val owner = ownerProvider.currentOwner()
        transactions.run {
            val workout = dao.getByIdOnce(owner, id)
            if (workout != null && workout.deletedAt == null && workout.notes != notes) {
                val now = System.currentTimeMillis()
                dao.updateNotes(owner, id, notes, now)
                outboxWriter.upsertWorkout(owner, workout.copy(notes = notes, updatedAt = now))
            }
        }
    }
}