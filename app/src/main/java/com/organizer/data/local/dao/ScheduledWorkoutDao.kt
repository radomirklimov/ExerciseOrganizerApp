package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledWorkoutDao {
    @Query("SELECT * FROM scheduled_workout WHERE ownerFirebaseUid = :owner AND deletedAt IS NULL ORDER BY dayOfWeek, startMinutes")
    fun observeAll(owner: String): Flow<List<ScheduledWorkoutEntity>>

    @Query("SELECT * FROM scheduled_workout WHERE ownerFirebaseUid = :owner AND deletedAt IS NULL")
    suspend fun getAllOnce(owner: String): List<ScheduledWorkoutEntity>

    @Query("SELECT * FROM scheduled_workout WHERE ownerFirebaseUid = :owner AND scheduleId = :id AND deletedAt IS NULL")
    suspend fun getById(owner: String, id: Long): ScheduledWorkoutEntity?

    @Query("SELECT * FROM scheduled_workout WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND deletedAt IS NULL")
    suspend fun getForWorkout(owner: String, workoutId: Long): List<ScheduledWorkoutEntity>

    @Query("SELECT * FROM scheduled_workout WHERE ownerFirebaseUid = :owner AND syncId = :syncId")
    suspend fun findBySyncId(owner: String, syncId: String): ScheduledWorkoutEntity?

    @Insert
    suspend fun insert(entry: ScheduledWorkoutEntity): Long

    @Update
    suspend fun update(entry: ScheduledWorkoutEntity)

    @Query("UPDATE scheduled_workout SET reminderNote = :note, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE ownerFirebaseUid = :owner AND scheduleId = :id")
    suspend fun updateNote(owner: String, id: Long, note: String, updatedAt: Long)

    @Query("UPDATE scheduled_workout SET deletedAt = :deletedAt, syncStatus = 'PENDING_DELETE' WHERE ownerFirebaseUid = :owner AND scheduleId = :id")
    suspend fun softDelete(owner: String, id: Long, deletedAt: Long)

    @Query("UPDATE scheduled_workout SET syncStatus = 'SYNCED', serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyAcknowledgementMetadata(owner: String, syncId: String, serverVersion: Long, updatedAt: Long)

    @Query("UPDATE scheduled_workout SET deletedAt = :deletedAt, syncStatus = 'SYNCED', serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyRemoteDelete(owner: String, syncId: String, deletedAt: Long, serverVersion: Long, updatedAt: Long): Int

    @Query("UPDATE scheduled_workout SET dayOfWeek = :dayOfWeek, scheduledDateKey = :scheduledDateKey, startMinutes = :startMinutes, reminderEnabled = :reminderEnabled, reminderMinutesBefore = :reminderMinutesBefore, reminderNote = :reminderNote, syncStatus = 'SYNCED', serverVersion = :serverVersion, updatedAt = :updatedAt, deletedAt = NULL WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyRemoteUpsert(owner: String, syncId: String, dayOfWeek: Int, scheduledDateKey: Int?, startMinutes: Int, reminderEnabled: Boolean, reminderMinutesBefore: Int, reminderNote: String, serverVersion: Long, updatedAt: Long): Int
}