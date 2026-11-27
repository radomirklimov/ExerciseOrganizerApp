package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.dao.PlannedSetDao
import com.organizer.data.local.dao.ScheduledWorkoutDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.WorkoutCompletionDao
import com.organizer.data.local.db.entities.SyncOutboxEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.data.remote.model.SyncMutationResultDto
import javax.inject.Inject
import javax.inject.Singleton

data class AcknowledgementResult(
    val acknowledged: Int = 0,
    val conflicted: Int = 0,
    val rejected: Int = 0,
    val skippedUnknown: Int = 0,
    val skippedTerminal: Int = 0,
    val skippedInvalid: Int = 0,
    val ownerChanged: Boolean = false,
)

@Singleton
class SyncOutboxAcknowledger @Inject constructor(
    private val ownerProvider: OwnerProvider,
    private val outboxDao: SyncOutboxDao,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val favoriteExerciseDao: FavoriteExerciseDao,
    private val scheduledWorkoutDao: ScheduledWorkoutDao? = null,
    private val workoutCompletionDao: WorkoutCompletionDao? = null,
    private val plannedSetDao: PlannedSetDao? = null,
    private val transactions: SyncTransactionRunner,
) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun acknowledge(owner: String, results: List<SyncMutationResultDto>): AcknowledgementResult {
        var acknowledged = 0
        var conflicted = 0
        var rejected = 0
        var skippedUnknown = 0
        var skippedTerminal = 0
        var skippedInvalid = 0

        for (result in results) {
            val outcome = try {
                transactions.run { acknowledgeOne(owner, result) }
            } catch (ex: OwnerChangedDuringAcknowledgementException) {
                return AcknowledgementResult(
                    acknowledged = acknowledged,
                    conflicted = conflicted,
                    rejected = rejected,
                    skippedUnknown = skippedUnknown,
                    skippedTerminal = skippedTerminal,
                    skippedInvalid = skippedInvalid,
                    ownerChanged = true,
                )
            }

            when (outcome) {
                AckOutcome.ACKNOWLEDGED -> acknowledged++
                AckOutcome.CONFLICTED -> conflicted++
                AckOutcome.REJECTED -> rejected++
                AckOutcome.SKIPPED_UNKNOWN -> skippedUnknown++
                AckOutcome.SKIPPED_TERMINAL -> skippedTerminal++
                AckOutcome.SKIPPED_INVALID -> skippedInvalid++
            }
        }

        return AcknowledgementResult(
            acknowledged = acknowledged,
            conflicted = conflicted,
            rejected = rejected,
            skippedUnknown = skippedUnknown,
            skippedTerminal = skippedTerminal,
            skippedInvalid = skippedInvalid,
        )
    }

    private suspend fun acknowledgeOne(owner: String, result: SyncMutationResultDto): AckOutcome {
        if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringAcknowledgementException()

        val mutation = outboxDao.findByMutationId(owner, result.mutationId)
            ?: return AckOutcome.SKIPPED_UNKNOWN
        if (mutation.syncStatus in SyncOutboxStatus.TERMINAL) {
            return AckOutcome.SKIPPED_TERMINAL
        }
        if (result.syncId != mutation.recordSyncId) {
            return AckOutcome.SKIPPED_INVALID
        }

        // DUPLICATE is a replay marker, not an acknowledgement. Use the stored
        // original outcome when present; legacy servers omit it, so their
        // duplicates keep the historical success interpretation.
        val effectiveStatus = if (result.status == STATUS_DUPLICATE) {
            result.originalStatus ?: STATUS_APPLIED
        } else {
            result.status
        }
        val status = when (effectiveStatus) {
            STATUS_APPLIED, STATUS_DUPLICATE -> SyncOutboxStatus.ACKNOWLEDGED
            STATUS_CONFLICT -> SyncOutboxStatus.CONFLICTED
            STATUS_REJECTED -> SyncOutboxStatus.REJECTED_TERMINAL
            else -> return AckOutcome.SKIPPED_INVALID
        }

        val now = System.currentTimeMillis()
        val serverVersion = result.serverVersion ?: mutation.baseVersion
        val serverUpdatedAt = result.serverUpdatedAt?.let(SyncTimestamps::parse) ?: now
        val updated = outboxDao.markTerminal(
            owner = owner,
            mutationId = mutation.mutationId,
            status = status,
            serverVersion = serverVersion,
            serverUpdatedAt = serverUpdatedAt,
            acknowledgedAt = now,
            terminalErrorCode = result.errorCode,
            allowedStatuses = SyncOutboxStatus.BLOCKING,
        )
        if (updated == 0) return AckOutcome.SKIPPED_TERMINAL

        if (status == SyncOutboxStatus.CONFLICTED &&
            mutation.entityType == SyncEntityTypes.WORKOUT_COMPLETION
        ) {
            resolveWorkoutCompletionConflict(owner, mutation, result, serverVersion)
        }

        if (status == SyncOutboxStatus.ACKNOWLEDGED) {
            val stillBlocked = outboxDao.hasBlockingMutationForRecord(
                owner, mutation.entityType, mutation.recordSyncId, SyncOutboxStatus.BLOCKING
            )
            val entityStatus = if (stillBlocked) {
                currentEntityStatus(owner, mutation)
            } else {
                "SYNCED"
            }
            if (entityStatus != null) {
                updateEntityMetadata(owner, mutation, serverVersion, serverUpdatedAt, entityStatus)
            }
        }

        return when (status) {
            SyncOutboxStatus.ACKNOWLEDGED -> AckOutcome.ACKNOWLEDGED
            SyncOutboxStatus.CONFLICTED -> AckOutcome.CONFLICTED
            SyncOutboxStatus.REJECTED_TERMINAL -> AckOutcome.REJECTED
            else -> AckOutcome.SKIPPED_INVALID
        }
    }

    private suspend fun currentEntityStatus(owner: String, mutation: com.organizer.data.local.db.entities.SyncOutboxEntity): String? =
        when (mutation.entityType) {
            SyncEntityTypes.WORKOUT -> workoutDao.findBySyncId(owner, mutation.recordSyncId)?.syncStatus
            SyncEntityTypes.CUSTOM_EXERCISE -> exerciseDao.findCustomBySyncId(owner, mutation.recordSyncId)?.syncStatus
            SyncEntityTypes.WORKOUT_EXERCISE -> workoutExerciseDao.findBySyncId(owner, mutation.recordSyncId)?.syncStatus
            SyncEntityTypes.FAVORITE_EXERCISE -> favoriteExerciseDao.findBySyncId(owner, mutation.recordSyncId)?.syncStatus
            SyncEntityTypes.SCHEDULED_WORKOUT -> scheduledWorkoutDao?.findBySyncId(owner, mutation.recordSyncId)?.syncStatus
            SyncEntityTypes.WORKOUT_COMPLETION -> workoutCompletionDao?.findBySyncId(owner, mutation.recordSyncId)?.syncStatus
            SyncEntityTypes.PLANNED_SET -> plannedSetDao?.findBySyncId(owner, mutation.recordSyncId)?.syncStatus
            else -> null
        }

    private suspend fun updateEntityMetadata(
        owner: String,
        mutation: com.organizer.data.local.db.entities.SyncOutboxEntity,
        serverVersion: Long,
        serverUpdatedAt: Long,
        syncStatus: String,
    ) {
        when (mutation.entityType) {
            SyncEntityTypes.WORKOUT -> workoutDao.applyAcknowledgementMetadata(
                owner, mutation.recordSyncId, serverVersion, serverUpdatedAt, syncStatus
            )
            SyncEntityTypes.CUSTOM_EXERCISE -> exerciseDao.applyAcknowledgementMetadata(
                owner, mutation.recordSyncId, serverVersion, serverUpdatedAt, syncStatus
            )
            SyncEntityTypes.WORKOUT_EXERCISE -> workoutExerciseDao.applyAcknowledgementMetadata(
                owner, mutation.recordSyncId, serverVersion, serverUpdatedAt, syncStatus
            )
            SyncEntityTypes.FAVORITE_EXERCISE -> favoriteExerciseDao.applyAcknowledgementMetadata(
                owner, mutation.recordSyncId, serverVersion, serverUpdatedAt, syncStatus
            )
            SyncEntityTypes.SCHEDULED_WORKOUT -> scheduledWorkoutDao?.applyAcknowledgementMetadata(
                owner, mutation.recordSyncId, serverVersion, serverUpdatedAt
            )
            SyncEntityTypes.WORKOUT_COMPLETION -> workoutCompletionDao?.applyAcknowledgementMetadata(
                owner, mutation.recordSyncId, serverVersion, serverUpdatedAt
            )
            SyncEntityTypes.PLANNED_SET -> plannedSetDao?.applyAcknowledgementMetadata(
                owner, mutation.recordSyncId, serverVersion, serverUpdatedAt, syncStatus
            )
        }
    }

    /**
     * WorkoutCompletion-only conflict resolution. The original mutation stays
     * CONFLICTED; a new mutationId carries the selected snapshot against the
     * acknowledged server version, so a transmitted mutation is never rewritten.
     */
    private suspend fun resolveWorkoutCompletionConflict(
        owner: String,
        mutation: com.organizer.data.local.db.entities.SyncOutboxEntity,
        result: com.organizer.data.remote.model.SyncMutationResultDto,
        serverVersion: Long,
    ) {
        val dao = workoutCompletionDao ?: return
        val serverSnapshot = WorkoutCompletionIdentity.snapshotFromPayload(result.serverPayload) ?: return
        val local = dao.findBySyncId(owner, mutation.recordSyncId) ?: return
        val workoutSyncId = workoutDao.getByIdOnce(owner, local.workoutId)?.syncId ?: return
        val serverWorkoutRef = result.serverPayload?.get("workoutSyncId")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
            ?: return
        if (serverWorkoutRef != workoutSyncId) return
        val serverDateKey = result.serverPayload?.get("dateKey")?.jsonPrimitive?.content?.toIntOrNull() ?: return
        if (serverDateKey != local.dateKey) return
        val localSnapshot = WorkoutCompletionIdentity.Snapshot(
            local.completedExercises, local.totalExercises, local.completedAt
        )
        val winner = WorkoutCompletionIdentity.selectWinner(localSnapshot, serverSnapshot)
        val payload = json.encodeToString(buildJsonObject {
            put("schemaVersion", SyncStagedApplier.SUPPORTED_SCHEMA_VERSION)
            put("workoutSyncId", workoutSyncId)
            put("dateKey", local.dateKey)
            put("completedExercises", winner.completedExercises)
            put("totalExercises", winner.totalExercises)
            put("completedAt", SyncTimestamps.format(winner.completedAtMillis))
        })
        // The resolved mutation uses a fresh id; the original remains terminal.
        outboxDao.insert(
            SyncOutboxEntity(
                mutationId = UUID.randomUUID().toString(),
                ownerFirebaseUid = owner,
                entityType = SyncEntityTypes.WORKOUT_COMPLETION,
                recordSyncId = mutation.recordSyncId,
                operation = "UPSERT",
                payload = payload,
                baseVersion = serverVersion,
                createdAt = System.currentTimeMillis(),
            )
        )
        // Retire the conflicted row (non-blocking terminal state) without
        // rewriting its payload or everTransmitted flag.
        outboxDao.markResolvedConflict(owner, mutation.mutationId)
        // Persist the selected snapshot locally as the durable resolution.
        dao.applyRemoteUpsert(
            owner, mutation.recordSyncId, winner.completedExercises, winner.totalExercises,
            winner.completedAtMillis, serverVersion, System.currentTimeMillis()
        )
    }

    private enum class AckOutcome {
        ACKNOWLEDGED,
        CONFLICTED,
        REJECTED,
        SKIPPED_UNKNOWN,
        SKIPPED_TERMINAL,
        SKIPPED_INVALID,
    }

    companion object {
        private const val STATUS_APPLIED = "APPLIED"
        private const val STATUS_DUPLICATE = "DUPLICATE"
        private const val STATUS_CONFLICT = "CONFLICT"
        private const val STATUS_REJECTED = "REJECTED"
    }
}

/** Thrown inside the acknowledgement transaction to roll back an account-switched call. */
class OwnerChangedDuringAcknowledgementException : RuntimeException()