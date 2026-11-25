package com.organizer.sync

import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.SyncStagedDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.repo.WorkoutNameNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStagedApplier @Inject constructor(
    private val ownerProvider: OwnerProvider,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val outboxDao: SyncOutboxDao,
    private val stagedDao: SyncStagedDao,
    private val transactions: SyncTransactionRunner,
    private val json: Json,
) {
    suspend fun process(owner: String, staged: SyncStagedRecordEntity): StagedApplyResult {
        if (staged.ownerFirebaseUid != owner) return StagedApplyResult.FAILED
        return try {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringStagedApplyException()
                // A newer delivery may have replaced this row (stagedId is
                // stable). Always apply the current version, never the snapshot
                // captured by the caller.
                val current = stagedDao.findByStagedId(owner, staged.stagedId)
                    ?: return@run StagedApplyResult.FAILED
                if (current.ownerFirebaseUid != owner) return@run StagedApplyResult.FAILED
                if (current.entityType !in APPLICABLE_TYPES) {
                    // Retain the staged payload. A future app version that adds
                    // an applier for this entity type must explicitly requeue
                    // these rows to PENDING; this version never does so.
                    return@run mark(current, SyncStagedStatus.UNSUPPORTED)
                }
                if (outboxDao.hasBlockingMutationForRecord(
                        owner, current.entityType, current.syncId, SyncOutboxStatus.BLOCKING
                    )
                ) {
                    return@run mark(current, SyncStagedStatus.LOCAL_PENDING, "Local mutation pending")
                }
                if (current.deleted) {
                    applyTombstone(owner, current)
                } else {
                    val payload = current.payload?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                        ?: return@run mark(current, SyncStagedStatus.FAILED, "Invalid payload")
                    val schemaVersion = payload["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
                    if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                        return@run mark(current, SyncStagedStatus.UNSUPPORTED_SCHEMA, "Unsupported schemaVersion")
                    }
                    when (current.entityType) {
                        SyncEntityTypes.WORKOUT -> applyWorkout(owner, current, payload)
                        SyncEntityTypes.CUSTOM_EXERCISE -> applyCustomExercise(owner, current, payload)
                        else -> mark(current, SyncStagedStatus.UNSUPPORTED)
                    }
                }
            }
        } catch (ex: OwnerChangedDuringStagedApplyException) {
            StagedApplyResult.OWNER_CHANGED
        } catch (ex: Exception) {
            mark(staged, SyncStagedStatus.FAILED, ex.javaClass.simpleName)
        }
    }

    private suspend fun applyTombstone(owner: String, staged: SyncStagedRecordEntity): StagedApplyResult {
        return when (staged.entityType) {
            SyncEntityTypes.WORKOUT -> {
                val local = workoutDao.findBySyncId(owner, staged.syncId)
                    ?: return mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
                if (local.serverVersion >= staged.serverVersion) {
                    mark(staged, SyncStagedStatus.APPLIED)
                } else {
                    workoutDao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
                    mark(staged, SyncStagedStatus.APPLIED)
                }
            }
            SyncEntityTypes.CUSTOM_EXERCISE -> {
                val local = exerciseDao.findCustomBySyncId(owner, staged.syncId)
                    ?: return mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
                if (local.serverVersion >= staged.serverVersion) {
                    mark(staged, SyncStagedStatus.APPLIED)
                } else {
                    exerciseDao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
                    mark(staged, SyncStagedStatus.APPLIED)
                }
            }
            else -> mark(staged, SyncStagedStatus.UNSUPPORTED)
        }
    }

    private suspend fun applyWorkout(owner: String, staged: SyncStagedRecordEntity, payload: JsonObject): StagedApplyResult {
        val name = payload.string("name") ?: return mark(staged, SyncStagedStatus.FAILED, "Missing name")
        val notes = payload.string("notes") ?: ""
        val normalizedName = WorkoutNameNormalizer.normalize(name)
        val local = workoutDao.findBySyncId(owner, staged.syncId)
        val tombstone = stagedDao.find(owner, staged.entityType, staged.syncId)

        if (staged.deleted) {
            if (local == null) return mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
            if (local.serverVersion >= staged.serverVersion) return mark(staged, SyncStagedStatus.APPLIED)
            workoutDao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        if (tombstone?.deleted == true && tombstone.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }
        if (local != null && local.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        if (local == null) {
            workoutDao.insert(
                WorkoutEntity(
                    name = name,
                    notes = notes,
                    ownerFirebaseUid = owner,
                    syncId = staged.syncId,
                    serverVersion = staged.serverVersion,
                    updatedAt = staged.serverUpdatedAt,
                    deletedAt = null,
                    syncStatus = "SYNCED",
                    normalizedName = normalizedName,
                )
            )
        } else {
            workoutDao.applyRemoteUpsert(
                owner, staged.syncId, name, notes, normalizedName,
                staged.serverVersion, staged.serverUpdatedAt,
            )
        }
        return mark(staged, SyncStagedStatus.APPLIED)
    }

    private suspend fun applyCustomExercise(owner: String, staged: SyncStagedRecordEntity, payload: JsonObject): StagedApplyResult {
        val name = payload.string("name") ?: return mark(staged, SyncStagedStatus.FAILED, "Missing name")
        val instructions = payload.stringList("instructions").orEmpty()
        val imageUrls = payload.stringList("imageUrls").orEmpty()
        val exerciseType = payload.string("exerciseType") ?: "REPS_WEIGHT"
        val isCustom = payload["isCustom"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        if (!isCustom) return mark(staged, SyncStagedStatus.FAILED, "Public exercise cannot be pulled as user data")

        val local = exerciseDao.findCustomBySyncId(owner, staged.syncId)
        val tombstone = stagedDao.find(owner, staged.entityType, staged.syncId)

        if (staged.deleted) {
            if (local == null) return mark(staged, SyncStagedStatus.TOMBSTONE_ONLY)
            if (local.serverVersion >= staged.serverVersion) return mark(staged, SyncStagedStatus.APPLIED)
            exerciseDao.applyRemoteDelete(owner, staged.syncId, staged.serverUpdatedAt, staged.serverVersion, staged.serverUpdatedAt)
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        if (tombstone?.deleted == true && tombstone.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }
        if (local != null && local.serverVersion >= staged.serverVersion) {
            return mark(staged, SyncStagedStatus.APPLIED)
        }

        if (local == null) {
            exerciseDao.insert(
                ExerciseEntity(
                    name = name,
                    instructions = instructions,
                    imageUrls = imageUrls,
                    isCustom = true,
                    exerciseType = exerciseType,
                    ownerFirebaseUid = owner,
                    syncId = staged.syncId,
                    serverVersion = staged.serverVersion,
                    updatedAt = staged.serverUpdatedAt,
                    syncStatus = "SYNCED",
                )
            )
        } else {
            exerciseDao.applyRemoteUpsert(
                owner, staged.syncId, name, instructions, imageUrls, exerciseType,
                staged.serverVersion, staged.serverUpdatedAt,
            )
        }
        return mark(staged, SyncStagedStatus.APPLIED)
    }

    private suspend fun mark(
        staged: SyncStagedRecordEntity,
        status: String,
        error: String? = null,
    ): StagedApplyResult {
        stagedDao.updateStatus(staged.ownerFirebaseUid, staged.stagedId, status, staged.retryCount, error, System.currentTimeMillis())
        return when (status) {
            SyncStagedStatus.APPLIED -> StagedApplyResult.APPLIED
            SyncStagedStatus.TOMBSTONE_ONLY -> StagedApplyResult.TOMBSTONE_ONLY
            SyncStagedStatus.WAITING_DEPENDENCY -> StagedApplyResult.WAITING_DEPENDENCY
            SyncStagedStatus.UNSUPPORTED -> StagedApplyResult.UNSUPPORTED
            SyncStagedStatus.UNSUPPORTED_SCHEMA -> StagedApplyResult.UNSUPPORTED_SCHEMA
            SyncStagedStatus.LOCAL_PENDING -> StagedApplyResult.LOCAL_PENDING
            SyncStagedStatus.FAILED -> StagedApplyResult.FAILED
            else -> StagedApplyResult.FAILED
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }

    private fun JsonObject.stringList(key: String): List<String>? =
        this[key]?.jsonArray?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        private val APPLICABLE_TYPES = setOf(SyncEntityTypes.WORKOUT, SyncEntityTypes.CUSTOM_EXERCISE)
    }
}

enum class StagedApplyResult {
    APPLIED,
    TOMBSTONE_ONLY,
    WAITING_DEPENDENCY,
    UNSUPPORTED,
    UNSUPPORTED_SCHEMA,
    LOCAL_PENDING,
    FAILED,
    OWNER_CHANGED,
}

/** Thrown inside the staged-apply transaction to abort an account-switched call. */
class OwnerChangedDuringStagedApplyException : RuntimeException()