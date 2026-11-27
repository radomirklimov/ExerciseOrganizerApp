package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import com.organizer.sync.OwnerChangedDuringLocalWriteException
import com.organizer.sync.SyncEntityTypes
import com.organizer.sync.SyncOperationLocks
import com.organizer.sync.SyncOutboxStatus
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncPayloadFactory
import com.organizer.sync.SyncTransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import java.util.UUID

class FavoriteExerciseRepository @Inject constructor(
    private val favoriteExerciseDao: FavoriteExerciseDao,
    private val exerciseDao: ExerciseDao,
    private val outboxDao: SyncOutboxDao,
    private val ownerProvider: OwnerProvider,
    private val transactions: SyncTransactionRunner,
    private val outboxWriter: SyncOutboxWriter,
    private val payloadFactory: SyncPayloadFactory,
    private val operationLocks: SyncOperationLocks,
) {
    fun observeFavoriteExerciseIds(): Flow<List<Long>> =
        ownerProvider.observeOwner().flatMapLatest { favoriteExerciseDao.observeFavoriteIds(it) }

    fun observeFavoriteExercises(): Flow<List<ExerciseEntity>> =
        ownerProvider.observeOwner().flatMapLatest { favoriteExerciseDao.observeFavoriteExercises(it) }

    fun observeIsFavorite(exerciseId: Long): Flow<Boolean> =
        ownerProvider.observeOwner().flatMapLatest { favoriteExerciseDao.observeIsFavorite(it, exerciseId) }

    /**
     * Deterministic cloud identity for a favorite: derived from the account
     * Firebase UID and the canonical tagged exercise reference. Never uses
     * Room primary keys. Stable across devices and re-adds.
     *
     * Returns null when the exercise reference is invalid, so callers can
     * fail closed and keep the favorite local-only.
     */
    fun deterministicSyncId(owner: String, exercise: ExerciseEntity): String? {
        val tag = if (exercise.isCustom) {
            val sid = exercise.syncId?.takeIf { runCatching { java.util.UUID.fromString(it) }.isSuccess } ?: return null
            "CUSTOM:$sid"
        } else {
            val pid = exercise.publicExerciseId?.takeIf { it > 0 } ?: return null
            "PUBLIC:$pid"
        }
        val canonical = "FAVORITE_EXERCISE:v1|$owner|$tag"
        return UUID.nameUUIDFromBytes(canonical.toByteArray(Charsets.UTF_8)).toString()
    }

    private suspend fun normalizeSyncIdIfNeeded(owner: String, exercise: ExerciseEntity, existing: FavoriteExerciseEntity): String {
        val deterministic = deterministicSyncId(owner, exercise)
        if (deterministic == null || existing.syncId == deterministic) return existing.syncId
        // Only normalize if no mutation has ever been transmitted for this record.
        val transmitted = outboxDao.findByRecordSyncId(owner, existing.syncId)
            .any { it.entityType == SyncEntityTypes.FAVORITE_EXERCISE && it.everTransmitted }
        if (transmitted) return existing.syncId
        favoriteExerciseDao.updateSyncId(owner, exercise.exerciseId, existing.syncId, deterministic, System.currentTimeMillis())
        return deterministic
    }

    suspend fun toggleFavorite(exerciseId: Long): Boolean {
        val owner = ownerProvider.currentOwner()
        return operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                val exercise = exerciseDao.getByIdOnce(owner, exerciseId)
                    ?: return@run false
                val existing = favoriteExerciseDao.findByOwnerAndExercise(owner, exerciseId)
                val now = System.currentTimeMillis()
                if (existing == null) {
                    val syncId = deterministicSyncId(owner, exercise)
                    val uploadable = syncId != null
                    val resolvedSyncId = syncId ?: UUID.randomUUID().toString()
                    val entity = FavoriteExerciseEntity(
                        exerciseId = exerciseId,
                        ownerFirebaseUid = owner,
                        syncId = resolvedSyncId,
                        updatedAt = now,
                    )
                    favoriteExerciseDao.insert(entity)
                    if (owner != OwnerProvider.GUEST && uploadable) {
                        val payload = payloadFactory.favoriteExercise(entity, exercise)
                        outboxWriter.upsertFavoriteExercise(owner, resolvedSyncId, entity.serverVersion, payload)
                    }
                    true
                } else if (existing.deletedAt == null) {
                    // active -> soft-delete (remove)
                    val normalizedSyncId = normalizeSyncIdIfNeeded(owner, exercise, existing)
                    favoriteExerciseDao.softDelete(owner, exerciseId, now, now)
                    if (owner != OwnerProvider.GUEST) {
                        val rows = outboxDao.findByRecordSyncId(owner, normalizedSyncId)
                            .filter { it.entityType == SyncEntityTypes.FAVORITE_EXERCISE }
                        if (rows.isNotEmpty() && rows.none { it.everTransmitted }) {
                            outboxDao.markSuperseded(owner, rows.map { it.mutationId }.toSet())
                        } else {
                            val payload = payloadFactory.favoriteExercise(existing.copy(deletedAt = now, updatedAt = now, syncId = normalizedSyncId), exercise)
                            outboxWriter.deleteFavoriteExercise(owner, normalizedSyncId, existing.serverVersion, payload)
                        }
                    }
                    true
                } else {
                    // deleted -> restore same row/syncId
                    favoriteExerciseDao.restoreLocal(owner, exerciseId, now)
                    if (owner != OwnerProvider.GUEST) {
                        val restored0 = favoriteExerciseDao.findByOwnerAndExercise(owner, exerciseId)!!
                        val resolvedSyncId = normalizeSyncIdIfNeeded(owner, exercise, restored0)
                        val restored = favoriteExerciseDao.findByOwnerAndExercise(owner, exerciseId)!!
                        val uploadable = deterministicSyncId(owner, exercise) != null
                        if (uploadable) {
                            val payload = payloadFactory.favoriteExercise(restored, exercise)
                            if (restored.serverVersion > 0) {
                                outboxWriter.restoreFavoriteExercise(owner, resolvedSyncId, restored.serverVersion, payload)
                            } else {
                                val pending = outboxDao.findByRecordSyncId(owner, resolvedSyncId)
                                    .filter { it.entityType == SyncEntityTypes.FAVORITE_EXERCISE && it.syncStatus == SyncOutboxStatus.PENDING_UPLOAD }
                                    .map { it.mutationId }.toSet()
                                if (pending.isNotEmpty()) outboxDao.markSuperseded(owner, pending)
                                outboxWriter.upsertFavoriteExercise(owner, resolvedSyncId, restored.serverVersion, payload)
                            }
                        }
                    }
                    true
                }
            }
        }
    }

    suspend fun addFavorite(exerciseId: Long) {
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                if (favoriteExerciseDao.isFavorite(owner, exerciseId)) return@run
                val exercise = exerciseDao.getByIdOnce(owner, exerciseId) ?: return@run
                val now = System.currentTimeMillis()
                val syncId = deterministicSyncId(owner, exercise)
                val existing = favoriteExerciseDao.findByOwnerAndExercise(owner, exerciseId)
                if (existing == null) {
                    val resolvedSyncId = syncId ?: UUID.randomUUID().toString()
                    favoriteExerciseDao.insert(
                        FavoriteExerciseEntity(exerciseId = exerciseId, ownerFirebaseUid = owner, syncId = resolvedSyncId, updatedAt = now)
                    )
                } else if (existing.deletedAt != null) {
                    favoriteExerciseDao.restoreLocal(owner, exerciseId, now)
                    if (syncId != null && existing.syncId != syncId) {
                        normalizeSyncIdIfNeeded(owner, exercise, favoriteExerciseDao.findByOwnerAndExercise(owner, exerciseId)!!)
                    }
                } else {
                    return@run
                }
                if (owner != OwnerProvider.GUEST && syncId != null) {
                    val ent = favoriteExerciseDao.findByOwnerAndExercise(owner, exerciseId)!!
                    val payload = payloadFactory.favoriteExercise(ent, exercise)
                    outboxWriter.upsertFavoriteExercise(owner, ent.syncId, ent.serverVersion, payload)
                }
            }
        }
    }

    suspend fun removeFavorite(exerciseId: Long) {
        val owner = ownerProvider.currentOwner()
        operationLocks.forOwner(owner).withLock {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringLocalWriteException()
                val existing = favoriteExerciseDao.findByOwnerAndExercise(owner, exerciseId) ?: return@run
                if (existing.deletedAt != null) return@run
                val exercise = exerciseDao.getByIdOnce(owner, exerciseId)
                val now = System.currentTimeMillis()
                val syncId = if (exercise != null) normalizeSyncIdIfNeeded(owner, exercise, existing) else existing.syncId
                favoriteExerciseDao.softDelete(owner, exerciseId, now, now)
                if (owner != OwnerProvider.GUEST) {
                    val rows = outboxDao.findByRecordSyncId(owner, syncId)
                        .filter { it.entityType == SyncEntityTypes.FAVORITE_EXERCISE }
                    if (rows.isNotEmpty() && rows.none { it.everTransmitted }) {
                        outboxDao.markSuperseded(owner, rows.map { it.mutationId }.toSet())
                    } else if (exercise != null) {
                        val payload = payloadFactory.favoriteExercise(existing.copy(deletedAt = now, updatedAt = now, syncId = syncId), exercise)
                        outboxWriter.deleteFavoriteExercise(owner, syncId, existing.serverVersion, payload)
                    }
                }
            }
        }
    }
}