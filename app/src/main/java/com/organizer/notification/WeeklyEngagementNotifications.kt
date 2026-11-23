package com.organizer.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.organizer.R
import com.organizer.data.preferences.SettingsRepository
import java.util.Calendar

/**
 * Weekly "we miss you" engagement notification. One message is shown every
 * Sunday at 18:00; the messages are drawn in a random order that uses the
 * whole list once before repeating. Everything is scheduled with a
 * wake-up alarm so the nudge arrives while the app is closed and the phone is
 * locked, and it is skipped entirely when the Settings "Notification Alerts"
 * switch is off.
 */
object WeeklyEngagementNotifications {

    const val CHANNEL_ID = "weekly_engagement"
    const val NOTIFICATION_ID = 9001
    private const val REQUEST_CODE = 9001

    /** Own preference file: message rotation state, not user settings. */
    private const val ROTATION_PREFS = "weekly_engagement"
    private const val KEY_REMAINING = "message_queue"
    private const val KEY_LAST = "last_message"

    /**
     * Test override. While non-null, the notification is delivered once per
     * interval instead of every Sunday at 18:00, which makes the feature
     * verifiable without waiting a week. Set back to null to restore the real
     * weekly schedule.
     */
    private val TEST_INTERVAL_MS: Long? = null

    val MESSAGES = listOf(
        "👋 It's been a while! Your next workout is waiting for you.",
        "💪 Ready to pick up where you left off?",
        "🔥 Your workout plan is waiting. Let's make this week count!",
        "👀 Your fitness goals called. They're wondering how you're doing!",
        "🚀 One workout can be the start of a great week.",
        "🎯 Your next achievement is waiting for you!",
        "🙌 We've saved your progress. Ready to keep building on it?",
    )

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Weekly reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "A weekly nudge to keep you on track."
            }
        )
    }

    fun scheduleNext(context: Context) {
        if (!SettingsRepository.areNotificationsEnabled(context)) {
            cancel(context)
            return
        }

        val triggerAt = TEST_INTERVAL_MS?.let { System.currentTimeMillis() + it }
            ?: nextSundaySixPm()
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            // "Allow while idle" in both modes: without it the alarm is held
            // until the next Doze maintenance window, so a locked phone could
            // sit on the notification for many minutes (or hours for the
            // weekly one). These alarms wake the device even when the app
            // process is not running.
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }
    }

    /** Removes any pending alarm; called when alerts are switched off. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun show(context: Context) {
        // The Settings screen's "Notification Alerts" switch is the single
        // opt-out for these nudges.
        if (!SettingsRepository.areNotificationsEnabled(context)) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val message = nextMessage(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // A mipmap cannot be used as a small icon: the system tints it to a
            // flat silhouette (the grey square). Use the monochrome status icon
            // plus the real logo as the large icon, like workout reminders.
            .setSmallIcon(R.drawable.ic_stat_exercise_organizer)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.mipmap.exercise_organizer_logo_foreground,
                )
            )
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Returns the next message, cycling through the whole list in a fresh
     * random order before any message repeats. The rotation is persisted, so
     * closing the app or rebooting between two notifications does not reset
     * it.
     */
    private fun nextMessage(context: Context): String {
        val prefs = context.getSharedPreferences(ROTATION_PREFS, Context.MODE_PRIVATE)
        val shuffler = MessageShuffler(MESSAGES.size)
        shuffler.restore(
            remaining = prefs.getString(KEY_REMAINING, null)
                ?.split(',')
                ?.mapNotNull { it.toIntOrNull() }
                ?: emptyList(),
            lastDrawn = prefs.getInt(KEY_LAST, -1),
        )
        val index = shuffler.next()
        prefs.edit {
            putString(KEY_REMAINING, shuffler.remaining().joinToString(","))
            putInt(KEY_LAST, index)
        }
        return MESSAGES[index]
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WeeklyEngagementReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextSundaySixPm(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        cal.set(Calendar.HOUR_OF_DAY, 18)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val daysUntilSunday = (Calendar.SUNDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, daysUntilSunday)
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.DAY_OF_MONTH, 7)
        }
        return cal.timeInMillis
    }
}

class WeeklyEngagementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            WeeklyEngagementNotifications.scheduleNext(context)
            return
        }
        WeeklyEngagementNotifications.show(context)
        WeeklyEngagementNotifications.scheduleNext(context)
    }
}
