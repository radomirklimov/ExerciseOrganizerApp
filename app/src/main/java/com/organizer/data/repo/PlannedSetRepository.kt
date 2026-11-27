package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.PlannedSetDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.sync.OwnerChangedDuringLocalWriteException
import com.organizer.sync.SyncEntityTypes
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncOutboxStatus
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncPayloadFactory
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class PlannedSetRepository @Inject constructor(
    private val dao: PlannedSetDao,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val outboxDao: SyncOutboxDao,
    private val ownerProvider: OwnerProvider,
    private val transactions: SyncTransactionRunner,
    private val outboxWriter: SyncOutboxWriter,
    private val payloadFactory: SyncPayloadFactory,
    private val operationLocks: SyncOperationLocks,
) {
    fun observePlannedSets(workoutId: Long, exerciseId: Long): Flow<List<PlannedSetEntity>> =
        dao.observePlannedSets(ownerProvider.currentOwner(), workoutId, exerciseId)

    suspend fun addSet(workoutId: Long, exerciseId: Long, setNumber: Int, reps: Int?, weight: Double?, durationSeconds: Int?) {
        require(setNumber >= 1) { "setNumber must be >= 1" }
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                val workout = workoutDao.getByIdOnce(owner, workoutId) ?: return@run
                val exercise = exerciseDao.getByIdOnce(owner, exerciseId) ?: return@run
                val now = System.currentTimeMillis()
                val entity = PlannedSetEntity(
                    workoutId = workoutId, exerciseId = exerciseId, setNumber = setNumber,
                    reps = reps, weight = weight, durationSeconds = durationSeconds,
                    ownerFirebaseUid = owner, updatedAt = now,
                )
                dao.upsert(entity)
                if (owner != OwnerProvider.GUEST) {
                    val payload = payloadFactory.plannedSet(entity, workout.syncId, exercise)
                    outboxWriter.upsertPlannedSet(owner, entity.syncId, entity.serverVersion, payload)
                }
            }
        }
    }

    suspend fun updateSet(workoutId: Long, exerciseId: Long, setNumber: Int, reps: Int?, weight: Double?, durationSeconds: Int?) {
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                val existing = dao.observePlannedSets(owner, workoutId, exerciseId).firstOrNull()?.firstOrNull { it.setNumber == setNumber } ?: return@run
                val workout = workoutDao.getByIdOnce(owner, workoutId) ?: return@run
                val exercise = exerciseDao.getByIdOnce(owner, exerciseId) ?: return@run
                val updated = existing.copy(reps = reps, weight = weight, durationSeconds = durationSeconds, updatedAt = System.currentTimeMillis(), syncStatus = "PENDING_UPLOAD")
                dao.upsert(updated)
                if (owner != OwnerProvider.GUEST) {
                    val payload = payloadFactory.plannedSet(updated, workout.syncId, exercise)
                    val rows = outboxDao.findByRecordSyncId(owner, existing.syncId)
                        .filter { it.entityType == SyncEntityTypes.PLANNED_SET }
                    if (rows.isNotEmpty() && rows.none { it.everTransmitted }) {
                        outboxDao.markSuperseded(owner, rows.map { it.mutationId }.toSet())
                    }
                    outboxWriter.upsertPlannedSet(owner, existing.syncId, existing.serverVersion, payload)
                }
            }
        }
    }

    suspend fun reorderSets(workoutId: Long, exerciseId: Long, ordered: List<PlannedSetEntity>) {
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                // Two-phase reorder avoids composite-PK collisions while swapping setNumbers.
                ordered.forEachIndexed { index, set ->
                    val existing = dao.findBySyncId(owner, set.syncId) ?: return@forEachIndexed
                    dao.updateSetNumber(owner, existing.syncId, -(index + 1), System.currentTimeMillis())
                }
                ordered.forEachIndexed { index, set ->
                    val existing = dao.findBySyncId(owner, set.syncId) ?: return@forEachIndexed
                    dao.updateSetNumber(owner, existing.syncId, index + 1, System.currentTimeMillis())
                    val updated = dao.findBySyncId(owner, existing.syncId)!!
                    if (owner != OwnerProvider.GUEST) {
                        val workout = workoutDao.getByIdOnce(owner, workoutId) ?: return@forEachIndexed
                        val exercise = exerciseDao.getByIdOnce(owner, exerciseId) ?: return@forEachIndexed
                        val payload = payloadFactory.plannedSet(updated, workout.syncId, exercise)
                        outboxWriter.upsertPlannedSet(owner, existing.syncId, existing.serverVersion, payload)
                    }
                }
            }
        }
    }

    suspend fun deleteSet(workoutId: Long, exerciseId: Long, setNumber: Int) {
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                val sets = dao.observePlannedSets(owner, workoutId, exerciseId).firstOrNull().orEmpty()
                val existing = sets.firstOrNull { it.setNumber == setNumber } ?: return@run
                val now = System.currentTimeMillis()
                dao.softDelete(owner, workoutId, exerciseId, setNumber, now, now)
                if (owner != OwnerProvider.GUEST) {
                    val rows = outboxDao.findByRecordSyncId(owner, existing.syncId)
                        .filter { it.entityType == SyncEntityTypes.PLANNED_SET }
                    if (rows.isNotEmpty() && rows.none { it.everTransmitted }) {
                        outboxDao.markSuperseded(owner, rows.map { it.mutationId }.toSet())
                    } else {
                        val workout = workoutDao.getByIdOnce(owner, workoutId)
                        val exercise = exerciseDao.getByIdOnce(owner, exerciseId)
                        if (workout != null && exercise != null) {
                            val payload = payloadFactory.plannedSet(existing.copy(deletedAt = now, updatedAt = now), workout.syncId, exercise)
                            outboxWriter.deletePlannedSet(owner, existing.syncId, existing.serverVersion, payload)
                        }
                    }
                }
            }
        }
    }
}