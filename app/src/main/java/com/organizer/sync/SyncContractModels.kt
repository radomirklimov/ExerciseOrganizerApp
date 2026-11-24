package com.organizer.sync

import kotlinx.serialization.json.JsonObject

/** UI/domain-independent results of translating backend responses. */
sealed interface SyncMutationOutcome {
    val mutationId: String
    val syncId: String

    data class Applied(
        override val mutationId: String,
        override val syncId: String,
        val serverVersion: Long,
        val serverUpdatedAtMillis: Long?,
        val deleted: Boolean,
    ) : SyncMutationOutcome

    data class Duplicate(
        override val mutationId: String,
        override val syncId: String,
        val serverVersion: Long?,
        val serverUpdatedAtMillis: Long?,
        val deleted: Boolean,
    ) : SyncMutationOutcome

    data class Conflict(
        override val mutationId: String,
        override val syncId: String,
        val serverVersion: Long?,
        val serverUpdatedAtMillis: Long?,
        val deleted: Boolean,
        val serverPayload: JsonObject?,
        val errorCode: String?,
        val errorMessage: String?,
    ) : SyncMutationOutcome

    data class Rejected(
        override val mutationId: String,
        override val syncId: String,
        val errorCode: String?,
        val errorMessage: String?,
    ) : SyncMutationOutcome
}

data class SyncChangeRecord(
    val entityType: String,
    val syncId: String,
    val serverVersion: Long,
    val serverUpdatedAtMillis: Long,
    val deleted: Boolean,
    val payload: JsonObject?,
)

data class SyncChanges(
    val records: List<SyncChangeRecord>,
    val nextCursor: String,
    val hasMore: Boolean,
    val serverTimeMillis: Long,
)

sealed interface OutboxMappingResult {
    data class Success(val request: com.organizer.data.remote.model.SyncMutationRequestDto) : OutboxMappingResult
    data class Failure(val reason: String) : OutboxMappingResult
}
