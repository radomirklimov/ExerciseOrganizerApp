package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.SyncStagedDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SuggestedExerciseRow
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import com.organizer.sync.OwnerChangedDuringLocalWriteException
import com.organizer.sync.SyncEntityTypes
import com.organizer.sync.SyncOutboxStatus
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncPayloadFactory
import com.organizer.sync.SyncTransactionRunner
import com.organizer.sync.WorkoutExerciseIdentityParser
import com.organizer.sync.ExerciseReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.organizer.sync.SyncOperationLocks
import javax.inject.Inject
import kotlin.random.Random

/** Outcome of a local association change, reported without exposing IDs. */
enum class AssociationChange {
    CREATED,
    RESTORED,
    DELETED,
    NOOP,
    BLOCKED,
}

class WorkoutExerciseRepository @Inject constructor(
    private val dao: WorkoutExerciseDao,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val outboxDao: SyncOutboxDao,
    private val stagedDao: SyncStagedDao,
    private val ownerProvider: OwnerProvider,
    private val transactions: SyncTransactionRunner,
    private val outboxWriter: SyncOutboxWriter,
    private val payloadFactory: SyncPayloadFactory,
    private val operationLocks: com.organizer.sync.SyncOperationLocks,
) {
    private val reorderLocks = mutableMapOf<Long, Mutex>()

    private suspend fun <T> transactional(owner: String, block: suspend () -> T): T =
        operationLocks.forOwner(owner).withLock { transactions.run(block) }

    suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long): AssociationChange {
        val owner = ownerProvider.currentOwner()
        return transactional(owner) {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
            val existing = dao.findByPair(owner, workoutId, exerciseId)
            if (existing != null && existing.deletedAt == null) return@transactional AssociationChange.NOOP

            val now = System.currentTimeMillis()
            if (existing != null) {
                if (hasUnresolved(owner, existing.syncId)) return@transactional AssociationChange.BLOCKED
                val restored = existing.copy(deletedAt = null, updatedAt = now, syncStatus = "PENDING_UPLOAD")
                val payload = if (owner == OwnerProvider.GUEST) null
                    else payloadFor(owner, workoutId, exerciseId, restored) ?: return@transactional AssociationChange.BLOCKED
                dao.restoreLocal(owner, workoutId, exerciseId, now)
                if (owner != OwnerProvider.GUEST) {
                    val safePayload = requireNotNull(payload)
                    // A remote tombstone (restored from pull) has no local ACK row,
                    // so detect it from durable local metadata.
                    if (existing.serverVersion > 0) {
                        outboxWriter.restoreWorkoutExercise(owner, existing.syncId, existing.serverVersion, safePayload)
                    } else {
                        val pending = outboxDao.findByRecordSyncId(owner, existing.syncId)
                            .filter {
                                it.entityType == SyncEntityTypes.WORKOUT_EXERCISE &&
                                    it.syncStatus == SyncOutboxStatus.PENDING_UPLOAD
                            }
                            .map { it.mutationId }.toSet()
                        if (pending.isNotEmpty()) outboxDao.markSuperseded(owner, pending)
                        outboxWriter.upsertWorkoutExercise(owner, existing.syncId, existing.serverVersion, safePayload)
                    }
                }
                return@transactional AssociationChange.RESTORED
            }

            if (hasUnresolvedTombstone(owner, workoutId, exerciseId)) return@transactional AssociationChange.BLOCKED
            val link = WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                orderIndex = (dao.getMaxOrderIndex(owner, workoutId) ?: -1) + 1,
                ownerFirebaseUid = owner,
                updatedAt = now,
            )
            val payload = if (owner == OwnerProvider.GUEST) null
                else payloadFor(owner, workoutId, exerciseId, link) ?: return@transactional AssociationChange.BLOCKED
            dao.insert(link)
            if (owner != OwnerProvider.GUEST) {
                outboxWriter.upsertWorkoutExercise(owner, link.syncId, link.serverVersion, requireNotNull(payload))
            }
            AssociationChange.CREATED
        }
    }

    /**
     * Explicit resolution for a tombstone whose stable association syncId is
     * known (for example from the staged record). Reuses that identity and
     * emits RESTORE with the tombstone's acknowledged server version.
     */
    suspend fun restoreTombstonedAssociation(syncId: String): AssociationChange {
        val owner = ownerProvider.currentOwner()
        return transactional(owner) {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
            if (dao.findBySyncId(owner, syncId) != null) return@transactional AssociationChange.NOOP
            val tombstone = stagedDao.find(owner, SyncEntityTypes.WORKOUT_EXERCISE, syncId)
                ?: return@transactional AssociationChange.BLOCKED
            if (!tombstone.deleted || tombstone.processingStatus !in setOf(
                    com.organizer.data.local.db.entities.SyncStagedStatus.WAITING_DEPENDENCY,
                    com.organizer.data.local.db.entities.SyncStagedStatus.FAILED,
                    com.organizer.data.local.db.entities.SyncStagedStatus.CONFLICT_BLOCKED,
                    com.organizer.data.local.db.entities.SyncStagedStatus.TOMBSTONE_ONLY,
                    com.organizer.data.local.db.entities.SyncStagedStatus.APPLIED,
                )
            ) {
                return@transactional AssociationChange.BLOCKED
            }
            val identity = WorkoutExerciseIdentityParser.parse(tombstone.payload, tombstone.syncId)
            if (!identity.hasUsablePair) return@transactional AssociationChange.BLOCKED
            val workoutId = workoutDao.findBySyncId(owner, identity.workoutSyncId!!)?.workoutId
                ?: return@transactional AssociationChange.BLOCKED
            val exerciseId = when (val ref = identity.exerciseRef!!) {
                is ExerciseReference.Public -> exerciseDao.findPublicByCatalogId(ref.catalogId)?.exerciseId
                is ExerciseReference.Custom -> exerciseDao.findCustomBySyncId(owner, ref.syncId)?.exerciseId
            } ?: return@transactional AssociationChange.BLOCKED
            if (hasUnresolved(owner, syncId)) return@transactional AssociationChange.BLOCKED

            val now = System.currentTimeMillis()
            val restored = WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                orderIndex = (dao.getMaxOrderIndex(owner, workoutId) ?: -1) + 1,
                ownerFirebaseUid = owner,
                syncId = syncId,
                updatedAt = now,
                serverVersion = tombstone.serverVersion,
                syncStatus = "PENDING_UPLOAD",
            )
            val payload = if (owner == OwnerProvider.GUEST) null
                else payloadFor(owner, workoutId, exerciseId, restored) ?: return@transactional AssociationChange.BLOCKED
            dao.insert(restored)
            if (owner != OwnerProvider.GUEST) {
                outboxWriter.restoreWorkoutExercise(owner, syncId, tombstone.serverVersion, requireNotNull(payload))
            }
            stagedDao.updateStatus(
                owner, tombstone.stagedId,
                com.organizer.data.local.db.entities.SyncStagedStatus.APPLIED,
                tombstone.retryCount, null, now,
            )
            AssociationChange.RESTORED
        }
    }

    fun observeExerciseIdsByWorkout(workoutId: Long): Flow<List<Long>> =
        dao.getExerciseIdsByWorkout(ownerProvider.currentOwner(), workoutId)

    fun observeAllWorkoutExercises(): Flow<List<WorkoutExerciseEntity>> =
        dao.getAll(ownerProvider.currentOwner())

    fun observeSuggestedExercises(limit: Int): Flow<List<ExerciseEntity>> =
        dao.observeSuggestedExerciseRows(ownerProvider.currentOwner()).map { rows ->
            mixSuggestedExercises(rows, limit)
        }

    fun observeWorkoutExerciseById(workoutId: Long, exerciseId: Long): Flow<WorkoutExerciseEntity?> =
        dao.getWorkoutExercise(ownerProvider.currentOwner(), workoutId, exerciseId)

    suspend fun updateExerciseOrder(workoutId: Long, exercises: List<ExerciseEntity>): AssociationChange {
        val lock = synchronized(reorderLocks) { reorderLocks.getOrPut(workoutId) { Mutex() } }
        return lock.withLock {
            val owner = ownerProvider.currentOwner()
            transactional(owner) {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                val now = System.currentTimeMillis()
                val changes = mutableListOf<Pair<WorkoutExerciseEntity, String?>>()
                exercises.forEachIndexed { index, exercise ->
                    val link = dao.findByPair(owner, workoutId, exercise.exerciseId)
                        ?: return@transactional AssociationChange.BLOCKED
                    if (link.deletedAt != null || hasUnresolved(owner, link.syncId)) {
                        return@transactional AssociationChange.BLOCKED
                    }
                    val payload = if (owner == OwnerProvider.GUEST) null
                        else payloadFor(
                            owner, workoutId, exercise.exerciseId, link.copy(orderIndex = index, updatedAt = now)
                        ) ?: return@transactional AssociationChange.BLOCKED
                    changes += link.copy(orderIndex = index, updatedAt = now) to payload
                }
                changes.forEach { (link, _) ->
                    dao.updateOrderIndex(owner, workoutId, link.exerciseId, link.orderIndex, link.updatedAt)
                }
                if (owner != OwnerProvider.GUEST) {
                    changes.forEach { (link, payload) ->
                        if (payload != null) outboxWriter.upsertWorkoutExercise(owner, link.syncId, link.serverVersion, payload)
                    }
                }
                AssociationChange.NOOP
            }
        }
    }

    suspend fun deleteExerciseFromWorkout(workoutId: Long, exerciseId: Long): AssociationChange {
        val owner = ownerProvider.currentOwner()
        return transactional(owner) {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
            val link = dao.findByPair(owner, workoutId, exerciseId)
                ?: return@transactional AssociationChange.NOOP
            if (link.deletedAt != null) return@transactional AssociationChange.NOOP
            if (hasUnresolved(owner, link.syncId)) return@transactional AssociationChange.BLOCKED
            val now = System.currentTimeMillis()
            val payload = if (owner == OwnerProvider.GUEST) null
                else payloadFor(owner, workoutId, exerciseId, link.copy(deletedAt = now, updatedAt = now))
                    ?: return@transactional AssociationChange.BLOCKED
            dao.softDelete(owner, workoutId, exerciseId, now, now)
            if (owner != OwnerProvider.GUEST) {
                val rows = outboxDao.findByRecordSyncId(owner, link.syncId)
                    .filter { it.entityType == SyncEntityTypes.WORKOUT_EXERCISE }
                if (rows.isNotEmpty() && rows.none { it.everTransmitted }) {
                    outboxDao.markSuperseded(owner, rows.map { it.mutationId }.toSet())
                } else {
                    outboxWriter.deleteWorkoutExercise(owner, link.syncId, link.serverVersion, requireNotNull(payload))
                }
            }
            AssociationChange.DELETED
        }
    }

    private suspend fun unresolvedTombstones(owner: String) = stagedDao.getAll(owner).filter { staged ->
        // Any deleted staged WORKOUT_EXERCISE record is a tombstone, regardless
        // of its processing status: a tombstone that was already applied or
        // conflict-blocked still represents an unresolved server deletion that
        // must not be resurrected by a fresh association syncId.
        staged.ownerFirebaseUid == owner &&
            staged.entityType == SyncEntityTypes.WORKOUT_EXERCISE &&
            staged.deleted
    }

    private suspend fun hasUnresolvedTombstone(owner: String, workoutId: Long, exerciseId: Long): Boolean {
        val tombstones = unresolvedTombstones(owner)
        if (tombstones.isEmpty()) return false

        // A tombstone with no usable payload cannot be correlated with a pair.
        // With no local row to identify it, blocking all re-adds for this owner
        // is unavoidable: inventing identity would resurrect a server deletion.
        if (tombstones.any { tombstone ->
                val identity = WorkoutExerciseIdentityParser.parse(tombstone.payload, tombstone.syncId)
                !identity.hasUsablePair && dao.findBySyncId(owner, tombstone.syncId) == null
            }
        ) {
            return true
        }

        val workoutSyncId = workoutDao.getByIdOnce(owner, workoutId)?.syncId ?: return false
        val exercise = exerciseDao.getByIdOnce(owner, exerciseId) ?: return false
        val expectedRef = if (exercise.isCustom) {
            ExerciseReference.Custom(requireNotNull(exercise.syncId))
        } else {
            ExerciseReference.Public(requireNotNull(exercise.publicExerciseId))
        }
        return tombstones.any { tombstone ->
            val identity = WorkoutExerciseIdentityParser.parse(tombstone.payload, tombstone.syncId)
            identity.workoutSyncId == workoutSyncId && identity.exerciseRef == expectedRef
        }
    }

    private suspend fun hasUnresolved(owner: String, syncId: String): Boolean =
        outboxDao.findByRecordSyncId(owner, syncId).any {
            it.entityType == SyncEntityTypes.WORKOUT_EXERCISE &&
                (it.syncStatus in setOf(
                    SyncOutboxStatus.IN_FLIGHT,
                    SyncOutboxStatus.FAILED,
                    SyncOutboxStatus.CONFLICTED,
                    SyncOutboxStatus.REJECTED_TERMINAL,
                ) || (it.syncStatus == SyncOutboxStatus.PENDING_UPLOAD && it.everTransmitted))
        }

    private suspend fun payloadFor(
        owner: String,
        workoutId: Long,
        exerciseId: Long,
        link: WorkoutExerciseEntity,
    ): String? {
        val workout = workoutDao.getByIdOnce(owner, workoutId) ?: return null
        val exercise = exerciseDao.getByIdOnce(owner, exerciseId) ?: return null
        return runCatching { payloadFactory.workoutExercise(link, workout.syncId, exercise) }.getOrNull()
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
