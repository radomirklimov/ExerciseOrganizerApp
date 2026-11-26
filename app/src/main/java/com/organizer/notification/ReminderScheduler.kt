package com.organizer.notification

import com.organizer.data.local.db.entities.ScheduledWorkoutEntity

/**
 * Narrow test seam for device-local reminder alarms. The production
 * implementation is [WorkoutReminderScheduler]; there is no second scheduling
 * system.
 */
interface ReminderScheduler {
    fun schedule(entry: ScheduledWorkoutEntity)
    fun cancel(scheduleId: Long)
    fun rescheduleAll(entries: List<ScheduledWorkoutEntity>)
}
