package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.SyncCursorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncCursorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_cursor WHERE ownerFirebaseUid = :owner")
    suspend fun get(owner: String): SyncCursorEntity?

    @Query("SELECT * FROM sync_cursor WHERE ownerFirebaseUid = :owner")
    fun observe(owner: String): Flow<SyncCursorEntity?>
}