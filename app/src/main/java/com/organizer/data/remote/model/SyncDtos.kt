package com.organizer.data.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SyncRecordRequestDto(
    val entityType: String,
    val clientId: String,
    val payload: JsonElement? = null,
    val updatedAt: String? = null,
    val deleted: Boolean = false,
)

@Serializable
data class SyncRecordResponseDto(
    val entityType: String,
    val clientId: String,
    val payload: JsonElement? = null,
    val updatedAt: String,
    val deleted: Boolean,
    val version: Long,
)

@Serializable
data class SyncPushRequestDto(
    val records: List<SyncRecordRequestDto> = emptyList(),
)

@Serializable
data class SyncPushResponseDto(
    val records: List<SyncRecordResponseDto>,
    val serverTime: String,
)

@Serializable
data class SyncChangesResponseDto(
    val records: List<SyncRecordResponseDto>,
    val serverTime: String,
)