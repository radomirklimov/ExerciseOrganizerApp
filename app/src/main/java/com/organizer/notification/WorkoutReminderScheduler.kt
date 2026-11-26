package com.organizer.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the alarm side of reminders. Every schedule entry has at most one
 * pending alarm, identified by its [ScheduledWorkoutEntity.scheduleId].
 *
 * Alarms are scheduled one occurrence at a time. When one fires, the receiver
 * shows the notification and asks this class for the next week's occurrence.
 * Editing, disabling or removing an entry replaces or cancels the same alarm.
 */
@Singleton
class WorkoutReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {
    override fun schedule(entry: ScheduledWorkoutEntity) {
        if (!entry.reminderEnabled) {
            cancel(entry.scheduleId)
            return
        }

        val occurrence = if (entry.scheduledDateKey == null) {
            ReminderTimeCalculator.nextOccurrence(
                nowMillis = System.currentTimeMillis(),
                isoDayOfWeek = entry.dayOfWeek,
                startMinutes = entry.startMinutes,
                minutesBefore = entry.reminderMinutesBefore,
            )
        } else {
            // A one-time entry has no next week. Stop once its moment passed.
            ReminderTimeCalculator.occurrenceOnDate(
                nowMillis = System.currentTimeMillis(),
                dateKey = entry.scheduledDateKey,
                startMinutes = entry.startMinutes,
                minutesBefore = entry.reminderMinutesBefore,
            ) ?: run {
                // Cancel only the alarm: an already-posted reminder should
                // stay in the notification shade.
                cancelAlarm(entry.scheduleId)
                return
            }
        }

        scheduleAlarm(
            triggerAtMillis = occurrence.triggerAtMillis,
            pendingIntent = reminderPendingIntent(
                scheduleId = entry.scheduleId,
                workoutStartMillis = occurrence.workoutStartMillis,
            ),
        )
    }

    override fun rescheduleAll(entries: List<ScheduledWorkoutEntity>) {
        entries.forEach(::schedule)
    }

    override fun cancel(scheduleId: Long) {
        cancelAlarm(scheduleId)
        // Also drop a reminder that is already showing for this entry.
        NotificationManagerCompat.from(context).cancel(scheduleId.toInt())
    }

    private fun cancelAlarm(scheduleId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager?.cancel(
            reminderPendingIntent(
                scheduleId = scheduleId,
                workoutStartMillis = 0L,
            )
        )
    }

    private fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        // Exact alarms need a user-granted permission on Android 12+. When it
        // is missing, fall back to an inexact alarm that the system can still
        // deliver while the device is dozing.
        val canUseExactAlarm = context.canScheduleExactAlarms()

        if (canUseExactAlarm) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
                return
            } catch (e: SecurityException) {
                // The exact-alarm permission can be revoked between the check
                // and the call; fall through to the inexact alarm.
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun reminderPendingIntent(
        scheduleId: Long,
        workoutStartMillis: Long,
    ): PendingIntent {
        val intent = Intent(context, WorkoutReminderReceiver::class.java).apply {
            action = WorkoutReminderReceiver.ACTION_WORKOUT_REMINDER
            // A stable data URI makes the pending intent unique per entry even
            // when the request code is reused after an edit.
            data = Uri.parse("$REMINDER_URI_PREFIX$scheduleId")
            putExtra(WorkoutReminderReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(
                WorkoutReminderReceiver.EXTRA_WORKOUT_START_MILLIS,
                workoutStartMillis,
            )
        }

        return PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val REMINDER_URI_PREFIX = "workout-reminder://"
    }
}

/**
 * Whether the app may schedule exact alarms. Before Android 12 this is always
 * true; from Android 12 on the user controls it in system settings.
 */
fun Context.canScheduleExactAlarms(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
