package com.organizer.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.organizer.data.repo.ScheduleRepository
import javax.inject.Inject

/**
 * Fires for one scheduled workout reminder.
 *
 * The entry is read again at delivery time so a reminder that was edited or
 * removed while the alarm was pending does not show stale content. After
 * posting, the next weekly occurrence is scheduled.
 */
@AndroidEntryPoint
class WorkoutReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var reminderScheduler: WorkoutReminderScheduler

    @Inject
    lateinit var reminderNotifier: WorkoutReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WORKOUT_REMINDER) return

        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId <= 0L) return

        val workoutStartMillis = intent.getLongExtra(EXTRA_WORKOUT_START_MILLIS, 0L)
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entry = scheduleRepository.getEntry(scheduleId)
                if (entry == null || !entry.reminderEnabled) {
                    reminderScheduler.cancel(scheduleId)
                    return@launch
                }

                val workout = scheduleRepository.getWorkout(entry.workoutId)
                if (workout == null) {
                    // The workout was deleted. Its schedule entry normally
                    // cascades away, but make sure no stale alarm survives.
                    reminderScheduler.cancel(scheduleId)
                    return@launch
                }

                reminderNotifier.show(
                    entry = entry,
                    workoutName = workout.name,
                    workoutStartMillis = workoutStartMillis,
                )

                // Keep the weekly recurrence alive. A one-time entry is done
                // and has no next occurrence.
                if (entry.scheduledDateKey == null) {
                    reminderScheduler.schedule(entry)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_WORKOUT_REMINDER =
            "com.organizer.action.WORKOUT_REMINDER"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_WORKOUT_START_MILLIS = "extra_workout_start_millis"
    }
}
