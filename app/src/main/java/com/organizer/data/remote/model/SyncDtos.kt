package com.organizer.data.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Wire DTOs mirroring the backend Milestone 2 contract. */
@Serializable
data class SyncMutationRequestDto(
    val mutationId: String,
    val entityType: String,
    val syncId: String,
    val operation: String,
    val baseVersion: Long,
    val clientUpdatedAt: String,
    val payload: JsonObject? = null,
)

@Serializable
data class SyncPushRequestDto(
    val mutations: List<SyncMutationRequestDto> = emptyList(),
)

@Serializable
data class SyncMutationResultDto(
    val mutationId: String,
    val syncId: String,
    val status: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val serverVersion: Long? = null,
    val serverUpdatedAt: String? = null,
    val deleted: Boolean = false,
    val serverPayload: JsonObject? = null,
)

@Serializable
data class SyncPushResponseDto(
    val results: List<SyncMutationResultDto>,
    val serverTime: String,
)

@Serializable
data class SyncChangeRecordDto(
    val entityType: String,
    val syncId: String,
    val serverVersion: Long,
    val serverUpdatedAt: String,
    val deleted: Boolean,
    val payload: JsonObject? = null,
)

@Serializable
data class SyncChangesResponseDto(
    val records: List<SyncChangeRecordDto>,
    val nextCursor: String,
    val hasMore: Boolean,
    val serverTime: String,
)

@Serializable
data class ApiErrorDto(
    val error: String? = null,
    val message: String? = null,
)
