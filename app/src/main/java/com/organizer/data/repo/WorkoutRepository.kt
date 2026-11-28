package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ScheduledWorkoutDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.notification.ReminderScheduler
import com.organizer.data.local.db.entities.WorkoutWithExerciseCount
import com.organizer.sync.OwnerChangedDuringLocalWriteException
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
    private val ownerProvider: OwnerProvider,
    private val transactions: SyncTransactionRunner,
    private val outboxWriter: SyncOutboxWriter,
    private val scheduledWorkoutDao: ScheduledWorkoutDao? = null,
    private val reminderScheduler: ReminderScheduler? = null,
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
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
            val id = dao.insert(entity)
            outboxWriter.upsertWorkout(owner, entity.copy(workoutId = id))
            id
        }
    }

    fun observeWorkouts(): Flow<List<WorkoutEntity>> =
        ownerProvider.observeOwner().flatMapLatest { dao.getAll(it) }

    fun observeWorkoutsWithExerciseCount(): Flow<List<WorkoutWithExerciseCount>> =
        ownerProvider.observeOwner().flatMapLatest { dao.getWorkoutsWithExerciseCount(it) }

    fun observeWorkoutById(id: Long): Flow<WorkoutEntity?> {
        return dao.getById(ownerProvider.currentOwner(), id)
    }

    suspend fun deleteWorkout(id: Long) {
        val owner = ownerProvider.currentOwner()
        val now = System.currentTimeMillis()
        transactions.run {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
            val workout = dao.getByIdOnce(owner, id)
            if (workout != null && workout.deletedAt == null) {
                dao.softDelete(owner, id, now)
                outboxWriter.deleteWorkout(owner, workout.copy(deletedAt = now))
                // Cancel reminders for this workout's schedules; retain schedule rows/identities.
                scheduledWorkoutDao?.getForWorkout(owner, id)?.forEach { entry ->
                    reminderScheduler?.cancel(entry.scheduleId)
                }
            }
        }
    }

    suspend fun updateWorkoutNotes(id: Long, notes: String) {
        val owner = ownerProvider.currentOwner()
        transactions.run {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
            val workout = dao.getByIdOnce(owner, id)
            if (workout != null && workout.deletedAt == null && workout.notes != notes) {
                val now = System.currentTimeMillis()
                dao.updateNotes(owner, id, notes, now)
                outboxWriter.upsertWorkout(owner, workout.copy(notes = notes, updatedAt = now))
            }
        }
    }
}