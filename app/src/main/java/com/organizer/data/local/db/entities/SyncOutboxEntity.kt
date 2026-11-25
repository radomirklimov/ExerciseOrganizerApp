package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index("ownerFirebaseUid"),
        Index("mutationId", unique = true),
        Index(value = ["ownerFirebaseUid", "syncStatus"]),
        Index(value = ["ownerFirebaseUid", "entityType", "recordSyncId"]),
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val outboxId: Long = 0L,
    val mutationId: String,
    val ownerFirebaseUid: String,
    val entityType: String,
    val recordSyncId: String,
    val operation: String,
    val payload: String? = null,
    val baseVersion: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    val syncStatus: String = "PENDING_UPLOAD",
    val serverVersion: Long? = null,
    val serverUpdatedAt: Long? = null,
    val acknowledgedAt: Long? = null,
    val terminalErrorCode: String? = null,
    /** Durable proof that this mutation was handed to the network at least once. */
    val everTransmitted: Boolean = false,
)