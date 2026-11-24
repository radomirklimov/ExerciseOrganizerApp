package com.organizer.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_cursor",
    indices = [androidx.room.Index("ownerFirebaseUid", unique = true)]
)
data class SyncCursorEntity(
    @PrimaryKey(autoGenerate = true)
    val cursorId: Long = 0L,
    val ownerFirebaseUid: String,
    val cursor: String? = null,
    val lastSyncedAt: Long? = null,
)