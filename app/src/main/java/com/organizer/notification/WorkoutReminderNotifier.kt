package com.organizer.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.organizer.MainActivity
import com.organizer.R
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import com.organizer.presentation.screens.schedule.MotivationalPhrases
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and posts the local reminder notification. */
@Singleton
class WorkoutReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts before your scheduled workouts"
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * Returns true when the notification was posted. A denied notification
     * permission is not an error: the weekly alarm keeps running and the next
     * reminder is delivered once the user allows notifications.
     */
    fun show(
        entry: ScheduledWorkoutEntity,
        workoutName: String,
        workoutStartMillis: Long,
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        ensureChannel()

        if (workoutStartMillis <= 0L) return false

        val text = ReminderTextFormatter.format(
            workoutStartMillis = workoutStartMillis,
            note = MotivationalPhrases.resolve(context, entry.reminderNote),
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_exercise_organizer)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.mipmap.exercise_organizer_logo_foreground,
                )
            )
            .setContentTitle(workoutName)
            .setContentText(text.shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.longText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(entry.scheduleId))
            .build()

        return try {
            manager.notify(entry.scheduleId.toInt(), notification)
            true
        } catch (e: SecurityException) {
            // Permission was revoked between the check and notify().
            false
        }
    }

    private fun openAppPendingIntent(scheduleId: Long): PendingIntent? {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            ?: return null

        return PendingIntent.getActivity(
            context,
            scheduleId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "workout_reminders"
    }
}
