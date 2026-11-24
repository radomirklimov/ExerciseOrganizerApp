package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.remote.model.SyncChangesResponseDto
import com.organizer.data.remote.model.SyncMutationRequestDto
import com.organizer.data.remote.model.SyncMutationResultDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncContractMapper @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    fun toMutation(outbox: SyncOutboxEntity, expectedOwner: String): OutboxMappingResult {
        if (outbox.ownerFirebaseUid == OwnerProvider.GUEST) {
            return OutboxMappingResult.Failure("Guest mutations must not be uploaded")
        }
        if (outbox.ownerFirebaseUid != expectedOwner) {
            return OutboxMappingResult.Failure("Mutation belongs to a different owner")
        }
        if (!isValidUuid(outbox.mutationId)) return OutboxMappingResult.Failure("Invalid mutationId")
        if (!isValidUuid(outbox.recordSyncId)) return OutboxMappingResult.Failure("Invalid syncId")
        if (outbox.entityType !in SUPPORTED_ENTITY_TYPES) return OutboxMappingResult.Failure("Unsupported entityType")
        if (outbox.operation !in SUPPORTED_OPERATIONS) return OutboxMappingResult.Failure("Unsupported operation")
        if (outbox.baseVersion < 0) return OutboxMappingResult.Failure("Invalid baseVersion")

        val payload = if (outbox.payload == null) {
            null
        } else {
            parsePayload(outbox.payload) ?: return OutboxMappingResult.Failure("Invalid payload")
        }
        return OutboxMappingResult.Success(
            SyncMutationRequestDto(
                mutationId = outbox.mutationId,
                entityType = outbox.entityType,
                syncId = outbox.recordSyncId,
                operation = outbox.operation,
                baseVersion = outbox.baseVersion,
                clientUpdatedAt = SyncTimestamps.format(outbox.createdAt),
                payload = payload,
            )
        )
    }

    fun toOutcome(result: SyncMutationResultDto): SyncMutationOutcome = when (result.status) {
        STATUS_APPLIED -> SyncMutationOutcome.Applied(
            mutationId = result.mutationId,
            syncId = result.syncId,
            serverVersion = result.serverVersion ?: 0,
            serverUpdatedAtMillis = result.serverUpdatedAt?.let(SyncTimestamps::parse),
            deleted = result.deleted,
        )
        STATUS_DUPLICATE -> SyncMutationOutcome.Duplicate(
            mutationId = result.mutationId,
            syncId = result.syncId,
            serverVersion = result.serverVersion,
            serverUpdatedAtMillis = result.serverUpdatedAt?.let(SyncTimestamps::parse),
            deleted = result.deleted,
        )
        STATUS_CONFLICT -> SyncMutationOutcome.Conflict(
            mutationId = result.mutationId,
            syncId = result.syncId,
            serverVersion = result.serverVersion,
            serverUpdatedAtMillis = result.serverUpdatedAt?.let(SyncTimestamps::parse),
            deleted = result.deleted,
            serverPayload = result.serverPayload,
            errorCode = result.errorCode,
            errorMessage = result.errorMessage,
        )
        STATUS_REJECTED -> SyncMutationOutcome.Rejected(
            mutationId = result.mutationId,
            syncId = result.syncId,
            errorCode = result.errorCode,
            errorMessage = result.errorMessage,
        )
        else -> SyncMutationOutcome.Rejected(
            mutationId = result.mutationId,
            syncId = result.syncId,
            errorCode = "UNKNOWN_STATUS",
            errorMessage = "Unknown sync result status",
        )
    }

    fun toChanges(response: SyncChangesResponseDto): Result<SyncChanges> = runCatching {
        val records = response.records.map { record ->
            val timestamp = SyncTimestamps.parse(record.serverUpdatedAt)
                ?: error("Invalid serverUpdatedAt in change record")
            SyncChangeRecord(
                entityType = record.entityType,
                syncId = record.syncId,
                serverVersion = record.serverVersion,
                serverUpdatedAtMillis = timestamp,
                deleted = record.deleted,
                payload = record.payload,
            )
        }
        SyncChanges(
            records = records,
            nextCursor = response.nextCursor,
            hasMore = response.hasMore,
            serverTimeMillis = SyncTimestamps.parse(response.serverTime)
                ?: error("Invalid serverTime in changes response"),
        )
    }

    private fun parsePayload(raw: String?): JsonObject? {
        if (raw == null) return null
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        return element as? JsonObject
    }

    private fun isValidUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    companion object {
        const val STATUS_APPLIED = "APPLIED"
        const val STATUS_DUPLICATE = "DUPLICATE"
        const val STATUS_CONFLICT = "CONFLICT"
        const val STATUS_REJECTED = "REJECTED"

        private val SUPPORTED_ENTITY_TYPES = setOf(
            SyncEntityTypes.WORKOUT,
            SyncEntityTypes.CUSTOM_EXERCISE,
            SyncEntityTypes.WORKOUT_EXERCISE,
            SyncEntityTypes.EXERCISE_PROGRESS,
            SyncEntityTypes.PLANNED_SET,
            SyncEntityTypes.WORKOUT_COMPLETION,
            SyncEntityTypes.SCHEDULED_WORKOUT,
            SyncEntityTypes.FAVORITE_EXERCISE,
        )
        private val SUPPORTED_OPERATIONS = setOf("UPSERT", "DELETE", "RESTORE")
    }
}