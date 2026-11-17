package com.organizer.data.repo

import com.organizer.data.local.dao.ScheduledWorkoutDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Persists weekly workout slots; workout data itself stays in the workout tables. */
class ScheduleRepository @Inject constructor(
    private val scheduleDao: ScheduledWorkoutDao,
    private val workoutDao: WorkoutDao,
) {
    fun observeSchedule(): Flow<List<ScheduledWorkoutEntity>> =
        scheduleDao.observeAll()

    suspend fun getSchedule(): List<ScheduledWorkoutEntity> =
        scheduleDao.getAllOnce()

    suspend fun getEntry(scheduleId: Long): ScheduledWorkoutEntity? =
        scheduleDao.getById(scheduleId)

    suspend fun getWorkout(workoutId: Long): WorkoutEntity? =
        workoutDao.getByIdOnce(workoutId)

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
        )
    )

    suspend fun updateEntry(entry: ScheduledWorkoutEntity) =
        scheduleDao.update(entry)

    suspend fun updateNote(scheduleId: Long, note: String) =
        scheduleDao.updateNote(scheduleId, note)

    suspend fun deleteEntry(scheduleId: Long) =
        scheduleDao.delete(scheduleId)
}
