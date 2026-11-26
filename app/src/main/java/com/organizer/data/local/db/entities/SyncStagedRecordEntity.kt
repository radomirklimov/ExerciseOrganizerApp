package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_staged_record",
    indices = [
        Index("ownerFirebaseUid"),
        Index(value = ["ownerFirebaseUid", "processingStatus"]),
        Index(value = ["ownerFirebaseUid", "entityType", "syncId"], unique = true),
    ]
)
data class SyncStagedRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val stagedId: Long = 0L,
    val ownerFirebaseUid: String,
    val entityType: String,
    val syncId: String,
    val serverVersion: Long,
    val serverUpdatedAt: Long,
    val deleted: Boolean,
    val payload: String?,
    val processingStatus: String = SyncStagedStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
)

object SyncStagedStatus {
    const val PENDING = "PENDING"
    const val APPLIED = "APPLIED"
    const val TOMBSTONE_ONLY = "TOMBSTONE_ONLY"
    const val WAITING_DEPENDENCY = "WAITING_DEPENDENCY"
    const val UNSUPPORTED = "UNSUPPORTED"
    const val UNSUPPORTED_SCHEMA = "UNSUPPORTED_SCHEMA"
    const val LOCAL_PENDING = "LOCAL_PENDING"
    const val FAILED = "FAILED"
    /** Durable identity conflict requiring manual resolution; never auto-retried. */
    const val CONFLICT_BLOCKED = "CONFLICT_BLOCKED"
}