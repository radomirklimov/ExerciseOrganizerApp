package com.organizer.data.repo

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.ScheduledWorkoutDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScheduleRepository @Inject constructor(
    private val scheduleDao: ScheduledWorkoutDao,
    private val workoutDao: WorkoutDao,
    private val ownerProvider: OwnerProvider,
) {
    fun observeSchedule(): Flow<List<ScheduledWorkoutEntity>> =
        scheduleDao.observeAll(ownerProvider.currentOwner())

    suspend fun getSchedule(): List<ScheduledWorkoutEntity> =
        scheduleDao.getAllOnce(ownerProvider.currentOwner())

    suspend fun getEntry(scheduleId: Long): ScheduledWorkoutEntity? =
        scheduleDao.getById(ownerProvider.currentOwner(), scheduleId)

    suspend fun getWorkout(workoutId: Long): WorkoutEntity? =
        workoutDao.getByIdOnce(ownerProvider.currentOwner(), workoutId)

    suspend fun addEntry(
        workoutId: Long,
        dayOfWeek: Int,
        scheduledDateKey: Int?,
        startMinutes: Int,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Int,
        reminderNote: String,
    ): Long = scheduleDao.insert(
        ScheduledWorkoutEntity(
            workoutId = workoutId,
            dayOfWeek = dayOfWeek,
            scheduledDateKey = scheduledDateKey,
            startMinutes = startMinutes,
            reminderEnabled = reminderEnabled,
            reminderMinutesBefore = reminderMinutesBefore,
            reminderNote = reminderNote,
            ownerFirebaseUid = ownerProvider.currentOwner(),
        )
    )

    suspend fun updateEntry(entry: ScheduledWorkoutEntity) =
        scheduleDao.update(entry.copy(updatedAt = System.currentTimeMillis(), syncStatus = "PENDING_UPLOAD"))

    suspend fun updateNote(scheduleId: Long, note: String) =
        scheduleDao.updateNote(ownerProvider.currentOwner(), scheduleId, note, System.currentTimeMillis())

    suspend fun deleteEntry(scheduleId: Long) =
        scheduleDao.softDelete(ownerProvider.currentOwner(), scheduleId, System.currentTimeMillis())
}