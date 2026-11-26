package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.WorkoutCompletionDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.WorkoutCompletionEntity
import com.organizer.sync.OwnerChangedDuringLocalWriteException
import com.organizer.sync.SyncEntityTypes
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncOutboxStatus
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncPayloadFactory
import com.organizer.sync.SyncTransactionRunner
import com.organizer.sync.WorkoutCompletionIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class WorkoutCompletionRepository @Inject constructor(
    private val dao: WorkoutCompletionDao,
    private val workoutDao: WorkoutDao,
    private val outboxDao: SyncOutboxDao,
    private val ownerProvider: OwnerProvider,
    private val transactions: SyncTransactionRunner,
    private val outboxWriter: SyncOutboxWriter,
    private val payloadFactory: SyncPayloadFactory,
    private val operationLocks: SyncOperationLocks,
) {
    fun observeCompletions(): Flow<List<WorkoutCompletionEntity>> = dao.observeAll(ownerProvider.currentOwner())

    fun observeCompletionPercentByDate(): Flow<Map<Int, Int>> =
        dao.observeAll(ownerProvider.currentOwner()).map { completions ->
            completions.groupBy { it.dateKey }.mapValues { (_, day) ->
                val total = day.sumOf { it.totalExercises }
                if (total <= 0) 0 else (day.sumOf { it.completedExercises } * 100 + total / 2) / total
            }
        }

    suspend fun recordCompletion(dateKey: Int, workoutId: Long, completedExercises: Int, totalExercises: Int) {
        if (totalExercises <= 0) return
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                val workout = workoutDao.getByIdOnce(owner, workoutId) ?: return@run
                val syncId = WorkoutCompletionIdentity.syncId(owner, workout.syncId, dateKey)
                val existing = dao.findBySyncId(owner, syncId)
                // Legacy authenticated rows may carry a random syncId. Normalize
                // only when no mutation has ever been transmitted for that id.
                if (existing == null && owner != OwnerProvider.GUEST) {
                    val legacy = dao.observeAll(owner).firstOrNull()?.firstOrNull { it.dateKey == dateKey && it.workoutId == workoutId }
                    if (legacy != null && legacy.syncId != syncId) {
                        val transmitted = outboxDao.findByRecordSyncId(owner, legacy.syncId)
                            .any { it.entityType == SyncEntityTypes.WORKOUT_COMPLETION && it.everTransmitted }
                        if (!transmitted) {
                            dao.updateSyncId(owner, dateKey, workoutId, legacy.syncId, syncId, System.currentTimeMillis())
                        }
                    }
                }
                val now = System.currentTimeMillis()
                val completion = WorkoutCompletionEntity(
                    dateKey = dateKey,
                    workoutId = workoutId,
                    completedExercises = completedExercises.coerceIn(0, totalExercises),
                    totalExercises = totalExercises,
                    completedAt = now,
                    ownerFirebaseUid = owner,
                    syncId = syncId,
                    updatedAt = now,
                )
                dao.upsert(completion)
                if (owner != OwnerProvider.GUEST) {
                    val payload = payloadFactory.workoutCompletion(completion, workout.syncId)
                    if (existing != null) {
                        val rows = outboxDao.findByRecordSyncId(owner, syncId)
                            .filter { it.entityType == SyncEntityTypes.WORKOUT_COMPLETION }
                        if (rows.isNotEmpty() && rows.none { it.everTransmitted }) {
                            outboxDao.markSuperseded(owner, rows.map { it.mutationId }.toSet())
                        }
                    }
                    outboxWriter.upsertWorkoutCompletion(owner, syncId, completion.serverVersion, payload)
                }
            }
        }
    }
}