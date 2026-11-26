package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.dao.ScheduledWorkoutDao
import com.organizer.data.local.dao.SyncOutboxDao
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
    private val transactions: SyncTransactionRunner,
) {
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
        }
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